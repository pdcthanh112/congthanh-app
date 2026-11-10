import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createPool, runMigrations } from '@ecom/platform';
import { sql } from 'drizzle-orm';
import { drizzle } from 'drizzle-orm/node-postgres';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import * as schema from '../src/adapters/out/persistence/schema.js';
import { DrizzleUnitOfWork, readRepositories } from '../src/adapters/out/persistence/unit-of-work.js';
import { OrderEventsHandler } from '../src/application/order-events.handler.js';
import { ReviewService } from '../src/application/review.service.js';

const url = process.env.TEST_DATABASE_URL;
const SCHEMA = 'review_service';
const migrations = join(dirname(fileURLToPath(import.meta.url)), '..', 'drizzle');
const PREFIX = 'https://cdn.example/media/';

describe.skipIf(!url)('review-service + Postgres', () => {
  const pool = createPool({ url: url ?? '', schema: SCHEMA, poolMax: 8, applicationName: 'review-it' });
  const db = drizzle(pool, { schema, casing: 'snake_case' });
  let seq = 0;
  const clock = { now: () => new Date() };
  const uow = new DrizzleUnitOfWork(db);
  const sellers = new Map([['p1', 'seller-1']]);
  const service = new ReviewService(
    uow,
    readRepositories(db),
    { sellerOf: async (id) => sellers.get(id) },
    clock,
    { next: () => `r-${++seq}` },
    { mediaUrlPrefix: PREFIX },
  );
  const orders = new OrderEventsHandler(uow, clock);
  const buyer = { id: 'c1', name: 'Trần Thị Bình' };

  beforeAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await runMigrations(pool, SCHEMA, migrations);
  });
  afterAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await pool.end();
  });

  it('chưa thanh toán thì chưa được đánh giá; event lặp lại không ảnh hưởng', async () => {
    await orders.orderPlaced('e1', 'o1', 'c1', ['p1', 'p2', 'p1']);
    await expect(service.submit(buyer, 'o1', 'p1', { rating: 5 })).rejects.toMatchObject({ code: 'NOT_PURCHASED' });
    await orders.orderConfirmed('e2', 'o1');
    await orders.orderConfirmed('e2', 'o1');
    expect((await service.pendingReviews('c1', 10)).map((p) => p.productId).sort()).toEqual(['p1', 'p2']);
  });

  it('đánh giá cập nhật điểm tổng hợp + outbox; không đánh giá hai lần', async () => {
    const r = await service.submit(buyer, 'o1', 'p1', { rating: 4, content: 'Ổn' });
    await expect(service.submit(buyer, 'o1', 'p1', { rating: 5 })).rejects.toMatchObject({ code: 'ALREADY_REVIEWED' });
    expect((await service.read.summaries.get('p1')).stars).toEqual([0, 0, 0, 1, 0]);
    const types = await db.execute(sql`select type from review_service.outbox order by created_at`);
    expect(types.rows.map((t) => t.type)).toEqual([
      'ecommerce.review.v1.ReviewSubmitted',
      'ecommerce.review.v1.ProductRatingChanged',
    ]);
    expect((await service.pendingReviews('c1', 10)).map((p) => p.productId)).toEqual(['p2']);

    const edited = await service.edit('c1', r.id, 0, { rating: 2 });
    expect(edited.snapshot().version).toBe(1);
    expect((await service.read.summaries.get('p1')).stars).toEqual([0, 1, 0, 0, 0]);
  });

  it('ẩn đánh giá thì bỏ khỏi điểm; seller khác không trả lời được', async () => {
    const [r] = (await service.search({ productId: 'p1', sort: 'NEWEST', limit: 10, offset: 0 })).items;
    await expect(service.reply('seller-x', false, r!.id, 1, 'hi')).rejects.toMatchObject({ kind: 'FORBIDDEN' });
    const replied = await service.reply('seller-1', false, r!.id, 1, 'Cảm ơn');
    const hidden = await service.moderate('admin', r!.id, replied.snapshot().version, 'HIDDEN', 'spam');
    expect(hidden.status).toBe('HIDDEN');
    expect((await service.read.summaries.get('p1')).stars).toEqual([0, 0, 0, 0, 0]);
    expect((await service.search({ productId: 'p1', sort: 'NEWEST', limit: 10, offset: 0 })).total).toBe(0);
  });

  it('nhiều người đánh giá đồng thời không mất điểm', async () => {
    const n = 8;
    await Promise.all(
      Array.from({ length: n }, async (_, i) => {
        await orders.orderPlaced(`ep${i}`, `o-${i}`, `u${i}`, ['p9']);
        await orders.orderConfirmed(`ec${i}`, `o-${i}`);
      }),
    );
    await Promise.all(
      Array.from({ length: n }, (_, i) => service.submit({ id: `u${i}`, name: 'X' }, `o-${i}`, 'p9', { rating: 5 })),
    );
    expect((await service.read.summaries.get('p9')).stars[4]).toBe(n);
  });
});
