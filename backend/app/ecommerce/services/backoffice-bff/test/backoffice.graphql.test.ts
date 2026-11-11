import 'reflect-metadata';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import type { ProductSummary, StockLevel } from '@ecommerce/clients';
import { bootstrap, DomainError } from '@ecommerce/platform';
import { usd } from '@ecommerce/shared';
import type { INestApplication } from '@nestjs/common';
import { type CryptoKey, exportJWK, generateKeyPair, SignJWT } from 'jose';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { AppModule, type Backends } from '../src/app.module.js';
import { envSchema } from '../src/config/env.js';

// biome-ignore lint/suspicious/noExplicitAny: phản hồi GraphQL trong test được truy cập tự do
type Json = any;

/** Đầu-cuối qua HTTP với JWT thật (JWKS cục bộ) và backend giả: tập trung vào phân quyền. */
describe('backoffice-bff GraphQL', () => {
  const issuer = 'http://issuer.test/realms/ecommerce';
  let jwks: Server;
  let app: INestApplication;
  let url: string;
  let key: CryptoKey;
  const stock = new Map<string, StockLevel>([
    ['p1', lvl('p1', 5)],
    ['p2', lvl('p2', 0)],
  ]);
  const products = new Map<string, ProductSummary>([
    ['p1', prod('p1', 'seller-1')],
    ['p2', prod('p2', 'seller-2')],
  ]);
  const adjustments: { productId: string; change: number; by: string }[] = [];

  const backends: Backends = {
    products: {
      byIds: async (ids) =>
        new Map(ids.flatMap((id) => (products.has(id) ? [[id, products.get(id) as ProductSummary]] : []))),
      detail: async (id) => ({ id, name: id, slug: `s-${id}`, status: 'PUBLISHED', category: [{ categoryId: '3' }] }),
      bySlug: async (slug) => ({ id: slug.slice(2), name: slug }),
      list: async (page, limit) => ({
        paginationInfo: { page, limit, totalPage: 1, totalElement: 2 },
        responseList: [
          { id: 'p1', name: 'p1', status: 'PUBLISHED' },
          { id: 'p2', name: 'p2', status: 'PUBLISHED' },
        ],
      }),
      byCategory: async () => {
        throw new Error('unused');
      },
      search: async () => [],
    },
    inventory: {
      levels: async (ids) => new Map(ids.flatMap((id) => (stock.has(id) ? [[id, stock.get(id) as StockLevel]] : []))),
      adjust: async (a, by) => {
        adjustments.push({ productId: a.productId, change: a.change, by });
        const cur = stock.get(a.productId) as StockLevel;
        const next = cur.available + a.change;
        if (next < 0) return { success: false, newQuantity: cur.available, message: 'insufficient stock' };
        stock.set(a.productId, lvl(a.productId, next));
        return { success: true, newQuantity: next, message: '' };
      },
    },
    carts: {
      defaultCart: async (customerId) =>
        customerId === 'c1'
          ? { id: 9, customerId, items: [{ id: 1, productId: 'p1', quantity: 3, addedAt: null }] }
          : undefined,
    },
    promotions: {
      byCode: async (code) => {
        if (code !== 'GIAM10') throw DomainError.notFound('promotion');
        return {
          id: '1',
          code,
          type: 'DISCOUNT',
          value: 10,
          usageLimit: 100,
          description: '',
          status: 'ACTIVE',
          startDate: null,
          endDate: null,
        };
      },
    },
    orders: {
      get: async () => {
        throw DomainError.notFound('order');
      },
      cancel: async () => {},
    },
  };

  beforeAll(async () => {
    const pair = await generateKeyPair('RS256');
    key = pair.privateKey;
    const jwk = { ...(await exportJWK(pair.publicKey)), kid: 'k1', alg: 'RS256', use: 'sig' };
    jwks = createServer((_q, res) => res.end(JSON.stringify({ keys: [jwk] }))).listen(0);
    const env = envSchema.parse({
      NODE_ENV: 'test',
      LOG_LEVEL: 'error',
      OIDC_ISSUER_URI: issuer,
      JWT_JWKS_URL: `http://127.0.0.1:${(jwks.address() as AddressInfo).port}/certs`,
    });
    app = await bootstrap(AppModule.forRoot(env, backends), { port: 0, cors: false });
    url = `http://127.0.0.1:${(app.getHttpServer().address() as AddressInfo).port}/graphql`;
  });

  afterAll(async () => {
    await app?.close();
    jwks?.close();
  });

  const tokenFor = (sub: string, roles: string[]) =>
    new SignJWT({ realm_access: { roles } })
      .setProtectedHeader({ alg: 'RS256', kid: 'k1' })
      .setSubject(sub)
      .setIssuer(issuer)
      .setIssuedAt()
      .setExpirationTime('5m')
      .sign(key);

  async function gql(query: string, as?: [string, string[]]) {
    const headers: Record<string, string> = { 'content-type': 'application/json' };
    if (as) headers.authorization = `Bearer ${await tokenFor(...as)}`;
    const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify({ query }) });
    return (await res.json()) as { data?: Record<string, Json>; errors?: { extensions?: Json }[] };
  }

  const ADMIN: [string, string[]] = ['admin', ['ADMIN']];
  const SELLER1: [string, string[]] = ['seller-1', ['SELLER']];
  const CUSTOMER: [string, string[]] = ['c1', ['CUSTOMER']];

  it('khách thường bị chặn; chưa đăng nhập bị chặn', async () => {
    expect((await gql('{ adminProducts { totalItems } }', CUSTOMER)).errors?.[0]?.extensions?.code).toBe('FORBIDDEN');
    expect((await gql('{ adminProducts { totalItems } }')).errors?.[0]?.extensions?.code).toBe('UNAUTHENTICATED');
  });

  it('danh sách sản phẩm kèm tồn kho cho người vận hành', async () => {
    const r = await gql(
      '{ adminProducts { items { id sellerId status stock { available needsReorder status } } } }',
      ADMIN,
    );
    expect(r.errors).toBeUndefined();
    expect(r.data?.adminProducts.items).toEqual([
      {
        id: 'p1',
        sellerId: 'seller-1',
        status: 'PUBLISHED',
        stock: { available: 5, needsReorder: false, status: 'IN_STOCK' },
      },
      {
        id: 'p2',
        sellerId: 'seller-2',
        status: 'PUBLISHED',
        stock: { available: 0, needsReorder: true, status: 'OUT_OF_STOCK' },
      },
    ]);
  });

  it('người bán chỉ điều chỉnh kho sản phẩm của mình', async () => {
    const ok = await gql(
      'mutation { adjustInventory(input: {productId: "p1", change: 10, kind: RESTOCK, reason: "nhập lô 12"}) { newQuantity stock { available } } }',
      SELLER1,
    );
    expect(ok.data?.adjustInventory).toEqual({ newQuantity: 15, stock: { available: 15 } });
    expect(adjustments.at(-1)).toEqual({ productId: 'p1', change: 10, by: 'seller-1' });

    const other = await gql(
      'mutation { adjustInventory(input: {productId: "p2", change: 1, kind: RESTOCK, reason: "nhập lô"}) { newQuantity } }',
      SELLER1,
    );
    expect(other.errors?.[0]?.extensions?.code).toBe('FORBIDDEN');
  });

  it('kiểm tra chiều điều chỉnh và lỗi từ inventory-service', async () => {
    const wrongSign = await gql(
      'mutation { adjustInventory(input: {productId: "p1", change: 2, kind: DAMAGE, reason: "vỡ"}) { newQuantity } }',
      ADMIN,
    );
    expect(wrongSign.errors?.[0]?.extensions?.code).toBe('BAD_USER_INPUT');
    const tooMuch = await gql(
      'mutation { adjustInventory(input: {productId: "p2", change: -5, kind: LOSS, reason: "mất hàng"}) { newQuantity } }',
      ADMIN,
    );
    expect(tooMuch.errors?.[0]?.extensions).toMatchObject({ code: 'CONFLICT', reason: 'ADJUSTMENT_REJECTED' });
  });

  it('hỗ trợ xem giỏ của khách; khuyến mãi chỉ ADMIN', async () => {
    const cart = await gql(
      '{ customerCart(customerId: "c1") { id lines { quantity product { name stock { available } } } } }',
      ['sp', ['SUPPORT']],
    );
    expect(cart.errors).toBeUndefined();
    expect(cart.data?.customerCart.lines[0]).toMatchObject({ quantity: 3, product: { name: 'Sản phẩm p1' } });
    expect((await gql('{ customerCart(customerId: "nobody") { id } }', ADMIN)).data?.customerCart).toBeNull();
    expect((await gql('{ promotion(code: "giam10") { code value } }', ADMIN)).data?.promotion).toEqual({
      code: 'GIAM10',
      value: 10,
    });
    expect((await gql('{ promotion(code: "GIAM10") { code } }', SELLER1)).errors?.[0]?.extensions?.code).toBe(
      'FORBIDDEN',
    );
  });
});

function prod(id: string, sellerId: string): ProductSummary {
  return {
    id,
    sku: `SKU-${id}`,
    name: `Sản phẩm ${id}`,
    price: usd(2_999),
    originalPrice: usd(2_999),
    active: true,
    sellerId,
  };
}

function lvl(productId: string, available: number): StockLevel {
  return {
    productId,
    available,
    reserved: 0,
    total: available,
    reorderPoint: 2,
    status: available > 0 ? 'IN_STOCK' : 'OUT_OF_STOCK',
  };
}
