import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createPool, runMigrations } from '@ecommerce/platform';
import { sql } from 'drizzle-orm';
import { drizzle } from 'drizzle-orm/node-postgres';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import * as schema from '../src/adapters/out/persistence/schema.js';
import { DrizzleUnitOfWork, readRepositories } from '../src/adapters/out/persistence/unit-of-work.js';
import { CatalogService } from '../src/application/catalog.service.js';

/**
 * Kiểm thử tích hợp với Postgres thật (schema riêng, xoá sau khi chạy).
 * Bật bằng TEST_DATABASE_URL, ví dụ postgres://postgres@localhost:5432/ecommerce (CI dùng service container).
 */
const url = process.env.TEST_DATABASE_URL;
const SCHEMA = 'catalog_service';
const migrations = join(dirname(fileURLToPath(import.meta.url)), '..', 'drizzle');

describe.skipIf(!url)('catalog-service + Postgres', () => {
  const pool = createPool({ url: url ?? '', schema: SCHEMA, poolMax: 4, applicationName: 'catalog-it' });
  const db = drizzle(pool, { schema, casing: 'snake_case' });
  let seq = 0;
  const service = new CatalogService(
    new DrizzleUnitOfWork(db),
    readRepositories(db),
    { now: () => new Date() },
    { next: () => `it-${Date.now()}-${++seq}` },
  );

  beforeAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await runMigrations(pool, SCHEMA, migrations);
  });
  afterAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await pool.end();
  });

  it('seed dữ liệu tham chiếu', async () => {
    const roots = await service.read.categories.list({ parentId: null });
    expect(roots.map((c) => c.slug)).toContain('books');
    expect(await service.read.countries.findByIso('us')).toMatchObject({ isoCode: 'US' });
  });

  it('tạo danh mục con, ghi outbox cùng transaction, tính path', async () => {
    const books = await service.read.categories.findBySlug('books');
    const child = await service.createCategory({ name: 'Sách thiếu nhi', parentId: books!.id });
    expect(await service.read.categories.pathTo(child.id)).toEqual([books!.id, child.id]);
    const rows = await db.execute(
      sql`select type, aggregateid from catalog_service.outbox order by created_at desc limit 1`,
    );
    expect(rows.rows[0]).toMatchObject({
      type: 'ecommerce.catalog.v1.CategoryUpserted',
      aggregateid: `category:${child.id}`,
    });
  });

  it('khoá lạc quan: sửa bằng version cũ bị từ chối', async () => {
    const c = await service.createCategory({ name: 'Tạm thời' });
    await service.updateCategory(c.id, 0, { name: 'Tạm thời 2' });
    await expect(service.updateCategory(c.id, 0, { name: 'Tạm thời 3' })).rejects.toMatchObject({
      code: 'STALE_VERSION',
    });
  });

  it('không ẩn được danh mục còn con đang hoạt động', async () => {
    const books = await service.read.categories.findBySlug('books');
    await expect(service.deactivateCategory(books!.id, books!.snapshot().version)).rejects.toMatchObject({
      code: 'CATEGORY_HAS_CHILDREN',
    });
  });

  it('gán tag cho sản phẩm, tag trùng tên được dùng lại', async () => {
    const a = await service.createTag('Bán Chạy');
    const b = await service.createTag('bán chạy');
    expect(b.id).toBe(a.id);
    await service.setProductTags('p-1', [a.id, a.id]);
    expect((await service.read.tags.tagIdsOfProducts(['p-1'])).get('p-1')).toEqual([a.id]);
  });
});
