import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createPool, runMigrations } from '@ecom/platform';
import { sql } from 'drizzle-orm';
import { drizzle } from 'drizzle-orm/node-postgres';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import * as schema from '../src/adapters/out/persistence/schema.js';
import { DrizzleUnitOfWork, readRepositories } from '../src/adapters/out/persistence/unit-of-work.js';
import { ChatService } from '../src/application/chat.service.js';
import type { RealtimeEvent } from '../src/application/ports.js';

const url = process.env.TEST_DATABASE_URL;
const SCHEMA = 'message_service';
const migrations = join(dirname(fileURLToPath(import.meta.url)), '..', 'drizzle');

describe.skipIf(!url)('message-service + Postgres', () => {
  const pool = createPool({ url: url ?? '', schema: SCHEMA, poolMax: 8, applicationName: 'message-it' });
  const db = drizzle(pool, { schema, casing: 'snake_case' });
  let seq = 0;
  const events: { to: readonly string[]; support: boolean; e: RealtimeEvent }[] = [];
  let allowed = true;
  const chat = new ChatService(
    new DrizzleUnitOfWork(db),
    readRepositories(db),
    {
      publish: async (to, support, e) => void events.push({ to, support, e }),
      stream: () => {
        throw new Error('not used');
      },
    },
    { allow: async () => allowed },
    { sellerOf: async (p) => (p === 'p1' ? 'seller-1' : undefined) },
    { now: () => new Date() },
    { next: () => `id-${++seq}` },
    'https://cdn/',
  );
  const cus = { id: 'cus', isSupport: false };
  const sel = { id: 'seller-1', isSupport: false };
  const agent = { id: 'agent', isSupport: true };

  beforeAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await runMigrations(pool, SCHEMA, migrations);
  });
  afterAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await pool.end();
  });

  it('khách hỏi seller: hội thoại dùng lại, seq tăng, chưa đọc tính đúng, đẩy realtime cho cả hai', async () => {
    const a = await chat.startWithSeller(cus, 'p1', { body: 'Còn hàng không shop?', attachments: [] });
    const b = await chat.startWithSeller(cus, 'p1', { body: 'Size M nhé', attachments: [] });
    expect(b.conversation.id).toBe(a.conversation.id);
    expect(b.message.seq).toBe(2);
    expect(events.at(-1)?.to).toEqual(expect.arrayContaining(['cus', 'seller-1']));
    expect((await chat.list(sel, 1, 10)).items[0]).toMatchObject({ unread: 2, lastMessagePreview: 'Size M nhé' });
    expect((await chat.list(cus, 1, 10)).items[0]?.unread).toBe(0);
    await chat.send(sel, a.conversation.id, { body: 'Còn ạ', attachments: [] });
    await chat.markRead(sel, a.conversation.id, 3);
    expect(await chat.read.conversations.unreadTotal('seller-1')).toBe(0);
    expect(await chat.read.conversations.unreadTotal('cus')).toBe(1);
  });

  it('gửi lại cùng clientMessageId không tạo tin trùng; người ngoài không đọc được', async () => {
    const [c] = (await chat.list(cus, 1, 10)).items;
    const m1 = await chat.send(cus, c!.id, { body: 'ok', attachments: [], clientMessageId: 'x-1' });
    const m2 = await chat.send(cus, c!.id, { body: 'ok', attachments: [], clientMessageId: 'x-1' });
    expect(m2.id).toBe(m1.id);
    await expect(chat.history({ id: 'stranger', isSupport: true }, c!.id, null, 10)).rejects.toMatchObject({
      kind: 'NOT_FOUND',
    });
    const page = await chat.history(cus, c!.id, 3, 10);
    expect(page.map((m) => m.seq)).toEqual([2, 1]);
  });

  it('hỗ trợ: nhân viên thấy hàng chờ, trả lời được; vượt hạn mức thì bị chặn', async () => {
    const { conversation } = await chat.startSupport(cus, 'order-9', { body: 'Đơn chưa giao', attachments: [] });
    expect(events.at(-1)?.support).toBe(true);
    expect((await chat.list(agent, 1, 10)).items.map((c) => c.id)).toContain(conversation.id);
    await chat.send(agent, conversation.id, { body: 'Mình kiểm tra ngay', attachments: [] });
    allowed = false;
    await expect(chat.send(cus, conversation.id, { body: 'nhanh giúp', attachments: [] })).rejects.toMatchObject({
      code: 'RATE_LIMITED',
    });
    allowed = true;
    const out = await db.execute(sql`select count(*)::int as n from message_service.outbox`);
    expect(out.rows[0]).toMatchObject({ n: 6 });
  });

  it('gửi đồng thời vẫn có seq liên tục, không trùng', async () => {
    const { conversation } = await chat.startWithSeller({ id: 'cus2', isSupport: false }, 'p1', {
      body: 'hi',
      attachments: [],
    });
    await Promise.all(
      Array.from({ length: 10 }, (_, i) => chat.send(sel, conversation.id, { body: `m${i}`, attachments: [] })),
    );
    const seqs = (await chat.history(sel, conversation.id, null, 50)).map((m) => m.seq).sort((a, b) => a - b);
    expect(seqs).toEqual(Array.from({ length: 11 }, (_, i) => i + 1));
  });
});
