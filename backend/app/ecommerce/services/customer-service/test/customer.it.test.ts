import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createPool, runMigrations } from '@ecommerce/platform';
import { sql } from 'drizzle-orm';
import { drizzle } from 'drizzle-orm/node-postgres';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import * as schema from '../src/adapters/out/persistence/schema.js';
import { DrizzleUnitOfWork, readRepositories } from '../src/adapters/out/persistence/unit-of-work.js';
import { CustomerService } from '../src/application/customer.service.js';
import type { AddressDraft } from '../src/domain/shipping-address.js';

const url = process.env.TEST_DATABASE_URL;
const SCHEMA = 'customer_service';
const migrations = join(dirname(fileURLToPath(import.meta.url)), '..', 'drizzle');
const draft = (name: string): AddressDraft => ({
  fullName: name,
  phoneNumber: '+12125550100',
  label: 'HOME',
  countryCode: 'US',
  provinceCode: 'NY',
  districtCode: null,
  wardCode: null,
  addressLine: '350 5th Ave',
  postalCode: '10118',
  latitude: null,
  longitude: null,
});

describe.skipIf(!url)('customer-service + Postgres', () => {
  const pool = createPool({ url: url ?? '', schema: SCHEMA, poolMax: 8, applicationName: 'customer-it' });
  const db = drizzle(pool, { schema, casing: 'snake_case' });
  let seq = 0;
  let clock = Date.parse('2026-01-01T00:00:00Z');
  const service = new CustomerService(
    new DrizzleUnitOfWork(db),
    readRepositories(db),
    {
      now: () => {
        clock += 1000;
        return new Date(clock);
      },
    },
    { next: () => `a-${++seq}` },
  );

  beforeAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await runMigrations(pool, SCHEMA, migrations);
  });
  afterAll(async () => {
    await pool.query(`DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE`);
    await pool.end();
  });

  it('địa chỉ đầu tiên là mặc định; đổi mặc định; xoá mặc định thì chuyển sang địa chỉ khác', async () => {
    const a = await service.addAddress('u1', draft('A'), false);
    const b = await service.addAddress('u1', draft('B'), false);
    expect(a.isDefault).toBe(true);
    expect(b.isDefault).toBe(false);
    await service.setDefaultAddress('u1', b.id);
    expect((await service.read.addresses.findDefault('u1'))?.id).toBe(b.id);
    await service.deleteAddress('u1', b.id);
    expect((await service.read.addresses.findDefault('u1'))?.id).toBe(a.id);
  });

  it('thêm song song vẫn chỉ có một địa chỉ mặc định', async () => {
    await Promise.all(Array.from({ length: 5 }, (_, i) => service.addAddress('u2', draft(`P${i}`), true)));
    const list = await service.read.addresses.listByCustomer('u2');
    expect(list).toHaveLength(5);
    expect(list.filter((x) => x.isDefault)).toHaveLength(1);
  });

  it('không đọc/sửa địa chỉ của người khác', async () => {
    const a = await service.addAddress('u3', draft('C'), false);
    await expect(service.updateAddress('other', a.id, draft('X'))).rejects.toMatchObject({ kind: 'NOT_FOUND' });
  });

  it('wishlist idempotent và ghi outbox một lần', async () => {
    await service.addToWishlist('u4', 'p1');
    await service.addToWishlist('u4', 'p1');
    expect((await service.wishlist('u4', 1, 10)).total).toBe(1);
    const rows = await db.execute(sql`select count(*)::int as n from customer_service.outbox where aggregateid = 'u4'`);
    expect(rows.rows[0]).toMatchObject({ n: 1 });
    expect(await service.removeFromWishlist('u4', 'p1')).toBe(true);
    expect(await service.removeFromWishlist('u4', 'p1')).toBe(false);
  });

  it('lịch sử tìm kiếm: gộp từ khoá trùng, giữ tối đa 30', async () => {
    for (let i = 0; i < 35; i++) await service.recordSearch('u5', `kw ${i}`);
    await service.recordSearch('u5', 'kw 0');
    const recent = await service.recentSearches('u5', 30);
    expect(recent).toHaveLength(30);
    expect(recent[0]?.keyword).toBe('kw 0');
  });
});
