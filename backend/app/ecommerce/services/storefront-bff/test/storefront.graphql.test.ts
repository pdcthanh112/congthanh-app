import 'reflect-metadata';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { bootstrap } from '@ecom/platform';
import type { INestApplication } from '@nestjs/common';
import { exportJWK, generateKeyPair, SignJWT } from 'jose';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { AppModule } from '../src/app.module.js';
import { envSchema } from '../src/config/env.js';
import { fakeBackends } from './fakes.js';

// biome-ignore lint/suspicious/noExplicitAny: phản hồi GraphQL trong test được truy cập tự do
type Json = any;

/**
 * Kiểm thử đầu-cuối qua HTTP: schema GraphQL thật, xác thực JWT thật (JWKS cục bộ), backend giả.
 * Kiểm tra cả đường router Federation gọi vào (_entities) và việc gom lô DataLoader.
 */
describe('storefront-bff GraphQL', () => {
  const issuer = 'http://issuer.test/realms/ecommerce';
  let jwks: Server;
  let app: INestApplication;
  let url: string;
  let token: string;
  const f = fakeBackends();

  beforeAll(async () => {
    const { publicKey, privateKey } = await generateKeyPair('RS256');
    const jwk = { ...(await exportJWK(publicKey)), kid: 'k1', alg: 'RS256', use: 'sig' };
    jwks = createServer((_req, res) => {
      res.setHeader('content-type', 'application/json');
      res.end(JSON.stringify({ keys: [jwk] }));
    }).listen(0);
    const jwksPort = (jwks.address() as AddressInfo).port;
    token = await new SignJWT({ realm_access: { roles: ['CUSTOMER'] }, preferred_username: 'c1' })
      .setProtectedHeader({ alg: 'RS256', kid: 'k1' })
      .setSubject('c1')
      .setIssuer(issuer)
      .setIssuedAt()
      .setExpirationTime('5m')
      .sign(privateKey);
    const env = envSchema.parse({
      NODE_ENV: 'test',
      LOG_LEVEL: 'error',
      OIDC_ISSUER_URI: issuer,
      JWT_JWKS_URL: `http://127.0.0.1:${jwksPort}/certs`,
    });
    app = await bootstrap(AppModule.forRoot(env, f.backends), { port: 0, cors: false });
    url = `http://127.0.0.1:${(app.getHttpServer().address() as AddressInfo).port}/graphql`;
  });

  afterAll(async () => {
    await app?.close();
    jwks?.close();
  });

  async function gql(query: string, variables: Record<string, unknown> = {}, auth = false) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', ...(auth ? { authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify({ query, variables }),
    });
    return (await res.json()) as { data?: Record<string, Json>; errors?: { message: string; extensions?: Json }[] };
  }

  it('danh sách sản phẩm: giá + tồn kho gom thành một lần gọi mỗi service', async () => {
    const before = { ...f.calls };
    const r = await gql(`{ products(limit: 10) { totalItems items { id name slug images discountPercent
      price { amount formatted } categories { id } stock { status available } } } }`);
    expect(r.errors).toBeUndefined();
    const items = r.data?.products.items;
    expect(r.data?.products.totalItems).toBe(3);
    expect(items[0]).toMatchObject({
      id: 'p1',
      name: 'Áo thun',
      slug: 'slug-p1',
      images: ['/p1/1.jpg', '/p1/2.jpg'],
      discountPercent: 25,
      price: { amount: 150000 },
      categories: [{ id: '7' }],
      stock: { status: 'IN_STOCK', available: 10 },
    });
    expect(items[0].price.formatted).toMatch(/150\.000/);
    expect(f.calls.byIds - before.byIds).toBe(1);
    expect(f.calls.levels - before.levels).toBe(1);
  });

  it('tra theo slug; không tồn tại trả null; phải chọn đúng một khoá', async () => {
    expect((await gql(`{ product(slug: "slug-p2") { id name } }`)).data?.product).toEqual({
      id: 'p2',
      name: 'Quần jean',
    });
    expect((await gql(`{ product(id: "nope") { id } }`)).data?.product).toBeNull();
    expect((await gql(`{ product { id } }`)).errors?.[0]?.extensions?.code).toBe('BAD_USER_INPUT');
  });

  it('router Federation lấy Product qua _entities', async () => {
    const r = await gql(
      `query($r: [_Any!]!) { _entities(representations: $r) { ... on Product { id name price { amount } } } }`,
      {
        r: [
          { __typename: 'Product', id: 'p2' },
          { __typename: 'Product', id: 'p1' },
        ],
      },
    );
    expect(r.data?._entities).toEqual([
      { id: 'p2', name: 'Quần jean', price: { amount: 450000 } },
      { id: 'p1', name: 'Áo thun', price: { amount: 150000 } },
    ]);
  });

  it('giỏ hàng cần đăng nhập', async () => {
    const r = await gql(`{ myCart { itemCount } }`);
    expect(r.errors?.[0]?.extensions?.code).toBe('UNAUTHENTICATED');
  });

  it('thêm giỏ, báo giá mã, đặt hàng, xem đơn', async () => {
    const add = await gql(
      `mutation { addToCart(productId: "p1", quantity: 2) { itemCount checkoutReady subtotal { amount }
        lines { quantity product { name } lineTotal { amount } } } }`,
      {},
      true,
    );
    expect(add.errors).toBeUndefined();
    expect(add.data?.addToCart).toMatchObject({
      itemCount: 2,
      checkoutReady: true,
      subtotal: { amount: 300000 },
      lines: [{ quantity: 2, product: { name: 'Áo thun' }, lineTotal: { amount: 300000 } }],
    });

    const quote = await gql(`{ quotePromotion(code: "GIAM10") { valid total { amount } } }`, {}, true);
    expect(quote.data?.quotePromotion).toEqual({ valid: true, total: { amount: 270000 } });

    const tooMany = await gql(`mutation { addToCart(productId: "p2", quantity: 5) { itemCount } }`, {}, true);
    expect(tooMany.errors?.[0]?.extensions).toMatchObject({ code: 'CONFLICT', reason: 'INSUFFICIENT_STOCK' });

    const co = await gql(
      `mutation($i: CheckoutInput!) { checkout(input: $i) { orderCode status total { formatted } } }`,
      { i: { idempotencyKey: 'chk_0123456789abcdef', paymentMethod: 'CASH_ON_DELIVERY' } },
      true,
    );
    expect(co.data?.checkout).toMatchObject({ orderCode: 'ORD-1', status: 'PENDING' });

    const order = await gql(
      `{ order(orderCode: "ORD-1") { status lines { name quantity product { id stock { available } } } }
         myOrders { orderCode total { amount } } }`,
      {},
      true,
    );
    expect(order.errors).toBeUndefined();
    expect(order.data?.order.lines[0]).toMatchObject({ name: 'Áo thun', quantity: 2, product: { id: 'p1' } });
    expect(order.data?.myOrders).toEqual([{ orderCode: 'ORD-1', total: { amount: 300000 } }]);
  });
});
