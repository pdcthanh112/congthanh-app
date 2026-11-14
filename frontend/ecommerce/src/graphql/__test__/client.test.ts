// @vitest-environment node
import { afterEach, describe, expect, test, vi } from 'vitest';
import { getMyCart } from '@/api/cartApi';
import { getAllProduct } from '@/api/productApi';
import { gql, gqlWith, GraphQLRequestError, registerServerTokenProvider } from '@/graphql/client';
import { MyCartQuery, ProductsQuery } from '@/graphql/documents';

const money = (amount: number) => ({ amount, currency: 'VND', formatted: `${amount} ₫` });
const emptyCart = { id: 'c1', itemCount: 0, checkoutReady: true, subtotal: money(0), lines: [] };

function mockFetch(body: unknown, status = 200) {
  const fn = vi.fn(
    async (_url: string, _init?: RequestInit) =>
      new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
  );
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
  registerServerTokenProvider(async () => undefined);
});

describe('gql phía server', () => {
  test('gắn token từ provider đã đăng ký, gửi query và biến, trả data', async () => {
    registerServerTokenProvider(async () => 'tok');
    const fetchMock = mockFetch({ data: { myCart: emptyCart } });

    const data = await gql(MyCartQuery);

    expect(data.myCart.id).toBe('c1');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/graphql$/);
    expect((init!.headers as Record<string, string>).Authorization).toBe('Bearer tok');
    expect(JSON.parse(init!.body as string).query).toContain('myCart');
    expect(init!.cache).toBe('no-store');
  });

  test('anonymous: không gắn token và cache theo revalidate', async () => {
    registerServerTokenProvider(async () => 'tok');
    const fetchMock = mockFetch({ data: { products: { page: 1, limit: 1, totalItems: 0, totalPages: 0, items: [] } } });

    await gqlWith({ anonymous: true, revalidate: 60 }, ProductsQuery, { page: 1, limit: 1 });

    const init = fetchMock.mock.calls[0][1] as RequestInit & { next?: { revalidate: number } };
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
    expect(init.next).toEqual({ revalidate: 60 });
  });

  test('lỗi GraphQL thành GraphQLRequestError có code', async () => {
    mockFetch({ data: null, errors: [{ message: 'login required', extensions: { code: 'UNAUTHENTICATED' } }] });
    await expect(gql(MyCartQuery)).rejects.toMatchObject({ code: 'UNAUTHENTICATED' });
    await expect(gql(MyCartQuery)).rejects.toBeInstanceOf(GraphQLRequestError);
  });
});

describe('api', () => {
  test('getAllProduct chuyển dữ liệu GraphQL sang model và tính số trang', async () => {
    mockFetch({
      data: {
        products: {
          page: 1,
          limit: 2,
          totalItems: 3,
          totalPages: 2,
          items: [
            {
              id: 'p1',
              name: 'Áo',
              slug: 'ao',
              thumbnail: 'https://cdn/x.jpg',
              price: money(100),
              originalPrice: money(120),
              discountPercent: 17,
              ratingSummary: { average: 4.5, count: 2 },
              inWishlist: false,
            },
          ],
        },
      },
    });

    const res = await getAllProduct({ page: 1, limit: 2 }, { category: ['00000000-0000-0000-0000-000000000001'] });

    expect(res.data.totalPage).toBe(2);
    expect(res.data.responseList[0]).toMatchObject({ id: 'p1', slug: 'ao', thumbnail: { imagePath: 'https://cdn/x.jpg' } });
    expect(res.data.responseList[0].ratingSummary).toEqual({ average: 4.5, count: 2 });
  });

  test('getAllProduct lọc theo số sao trên trang trả về', async () => {
    const item = (id: string, average: number) => ({
      id,
      name: id,
      slug: id,
      thumbnail: null,
      price: money(100),
      originalPrice: null,
      discountPercent: 0,
      ratingSummary: { average, count: 1 },
      inWishlist: false,
    });
    mockFetch({ data: { products: { page: 1, limit: 10, totalItems: 2, totalPages: 1, items: [item('a', 4.6), item('b', 2)] } } });

    const res = await getAllProduct({ page: 1, limit: 10 }, { rating: 4 });

    expect(res.data.responseList.map((p) => p.id)).toEqual(['a']);
  });

  test('getMyCart trả giỏ hàng của người dùng', async () => {
    mockFetch({ data: { myCart: emptyCart } });
    await expect(getMyCart()).resolves.toMatchObject({ id: 'c1', itemCount: 0 });
  });
});
