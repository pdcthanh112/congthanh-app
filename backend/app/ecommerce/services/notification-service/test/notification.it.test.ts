import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createPool, runMigrations } from '@ecom/platform';
import { sql } from 'drizzle-orm';
import { drizzle } from 'drizzle-orm/node-postgres';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import * as schema from '../src/adapters/out/persistence/schema.js';
import { DrizzleUnitOfWork, readRepositories } from '../src/adapters/out/persistence/unit-of-work.js';
import { DeliveryWorker } from '../src/application/delivery.worker.js';
import { EventHandlers } from '../src/application/event-handlers.js';
import { NotificationService } from '../src/application/notification.service.js';
import { type ChannelSender, NoAddressError, type Recipient } from '../src/application/ports.js';
import type { Notification } from '../src/domain/notification.js';

const url = process.env.TEST_DATABASE_URL;
const SCHEMA = 'notification_service';
const migrations = join(dirname(fileURLToPath(import.meta.url)), '..', 'drizzle');
const vnd = (n: number) => ({ currencyCode: 'VND', units: BigInt(n), nanos: 0 });

describe.skipIf(!url)('notification-service + Postgres', () => {
  const pool = createPool({ url: url ?? '', schema: SCHEMA, poolMax: 8, applicationName: 'notification-it' });
  const db = drizzle(pool, { schema, casing: 'snake_case' });
  let seq = 0;
  let now = new Date('2026-05-01T07:00:00Z');
  const clock = { now: () => now };
  const read = readRepositories(db);
  const published: Notification[] = [];
  const service = new NotificationService(
    new DrizzleUnitOfWork(db),
    read,
    {
      notificationCreated: async (n) => void published.push(n),
      stream: () => {
        throw new Error('not used');
      },
    },
    clock,
    { next: () => `n-${++seq}` },
  );
  const people: Record<string, Recipient> = {
    c1: { id: 'c1', email: 'c1@example.com', enabled: true },
    c2: { id: 'c2', enabled: true },
    admin: { id: 'admin', email: 'admin@example.com', enabled: true },
  };
  const directory = { find: async (id: string) => people[id], withRole: async () => ['admin'] };
  const handlers = new EventHandlers(new DrizzleUnitOfWork(db), directory, service);
  const sent: string[] = [];
  let failEmail = 0;
  const email: ChannelSender = {
    channel: 'EMAIL',
    send: async (r, m) => {
      if (!r.email) throw new NoAddressError('no email');
      if (failEmail-- > 0) throw new Error('smtp down');
      sent.push(`${r.email}:${m.title}`);
    },
  };
  const worker = new DeliveryWorker(read, directory, [email], clock, { enabled: false, intervalMs: 0, batchSize: 50 });

  beforeAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await runMigrations(pool, SCHEMA, migrations);
  });
  afterAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await pool.end();
  });

  it('đặt hàng → thông báo in-app + email; event giao lại không gửi trùng', async () => {
    await handlers.orderPlaced('e1', 'order-0001', 'c1', vnd(250000));
    await handlers.orderPlaced('e1', 'order-0001', 'c1', vnd(250000));
    const list = await service.list('c1', false, 1, 10);
    expect(list.total).toBe(1);
    expect(list.items[0]?.title).toBe('Đặt hàng thành công');
    expect(published).toHaveLength(1);
    await worker.tick();
    expect(sent).toEqual(['c1@example.com:Đặt hàng thành công']);
  });

  it('thanh toán xong tra ra khách từ đơn đã nhớ', async () => {
    await handlers.orderConfirmed('e2', 'order-0001');
    expect(await service.read.notifications.unreadCount('c1')).toBe(2);
    expect(await service.markRead('c1', 'ALL')).toBe(2);
    expect(await service.read.notifications.unreadCount('c1')).toBe(0);
  });

  it('email lỗi tạm thời thì thử lại theo backoff; không có email thì SKIPPED', async () => {
    failEmail = 1;
    await handlers.orderCancelled('e3', 'order-0001', 'hết hàng');
    await worker.tick();
    let rows = await db.execute(
      sql`select status, attempts from notification_service.deliveries where status <> 'SENT' and channel = 'EMAIL'`,
    );
    expect(rows.rows).toEqual([{ status: 'PENDING', attempts: 1 }]);
    expect(await worker.tick()).toBe(0); // chưa tới giờ thử lại
    now = new Date(now.getTime() + 60_000);
    await worker.tick();
    rows = await db.execute(sql`select count(*)::int as n from notification_service.deliveries where status = 'SENT'`);
    expect(rows.rows[0]).toMatchObject({ n: 3 });

    await handlers.orderPlaced('e4', 'order-0002', 'c2', vnd(1));
    await worker.tick();
    rows = await db.execute(
      sql`select d.status from notification_service.deliveries d join notification_service.notifications n on n.id = d.notification_id where n.user_id = 'c2' and d.channel = 'EMAIL'`,
    );
    expect(rows.rows).toEqual([{ status: 'SKIPPED' }]);
  });

  it('wishlist: giảm giá ≥5% và có hàng lại thì báo người theo dõi', async () => {
    await handlers.wishlistAdded('w1', 'c2', 'p1');
    await handlers.productUpserted('p-a', 'p1', 'Áo thun', vnd(200000));
    await handlers.productUpserted('p-b', 'p1', 'Áo thun', vnd(195000)); // giảm 2.5%: bỏ qua
    await handlers.productUpserted('p-c', 'p1', 'Áo thun', vnd(150000));
    await handlers.stockChanged('s1', 'p1', 0);
    await handlers.stockChanged('s2', 'p1', 5);
    const types = (await service.list('c2', false, 1, 10)).items.map((n) => n.type);
    expect(types).toEqual(['BACK_IN_STOCK', 'PRICE_DROP', 'ORDER_PLACED']);
    await handlers.wishlistRemoved('w2', 'c2', 'p1');
    await handlers.productUpserted('p-d', 'p1', 'Áo thun', vnd(100000));
    expect((await service.list('c2', false, 1, 10)).total).toBe(3);
  });

  it('tắt nhóm wishlist qua in-app thì không còn thông báo đó', async () => {
    await service.updatePreferences('c2', [
      { category: 'WISHLIST', channel: 'IN_APP', enabled: false },
      { category: 'WISHLIST', channel: 'EMAIL', enabled: false },
      { category: 'WISHLIST', channel: 'WEB_PUSH', enabled: false },
      { category: 'WISHLIST', channel: 'FCM', enabled: false },
    ]);
    await handlers.wishlistAdded('w3', 'c2', 'p2');
    await handlers.stockChanged('s3', 'p2', 0);
    await handlers.stockChanged('s4', 'p2', 3);
    expect((await service.list('c2', false, 1, 10)).total).toBe(3);
  });

  it('tin nhắn mới gom theo khung 10 phút mỗi hội thoại', async () => {
    const t = new Date('2026-05-01T08:00:00Z');
    await handlers.messageSent('m1', 'conv-1', ['c1'], 'Còn hàng không?', t);
    await handlers.messageSent('m2', 'conv-1', ['c1'], 'Size M nhé', new Date(t.getTime() + 60_000));
    await handlers.messageSent('m3', 'conv-1', ['c1'], 'Alo', new Date(t.getTime() + 11 * 60_000));
    const msgs = (await service.list('c1', false, 1, 20)).items.filter((n) => n.type === 'NEW_MESSAGE');
    expect(msgs.map((n) => n.body)).toEqual(['Alo', 'Còn hàng không?']);
  });

  it('sắp hết hàng → báo admin', async () => {
    await handlers.lowStock('l1', 'p9', 2);
    expect((await service.list('admin', false, 1, 10)).items[0]?.type).toBe('LOW_STOCK');
  });
});
