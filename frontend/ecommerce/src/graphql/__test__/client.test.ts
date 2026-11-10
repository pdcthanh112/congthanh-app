/**
 * @jest-environment node
 */
import { getCartByCustomerId } from '@/api/cartApi';
import { getAllProduct } from '@/api/productApi';
import { gql, GraphQLRequestError } from '@/graphql/client';
import { MyCartQuery } from '@/graphql/documents';

jest.mock('next-auth/next', () => ({ getServerSession: async () => ({ accessToken: 'tok' }) }));
jest.mock('@/app/api/auth/[...nextauth]/route', () => ({ authOptions: {} }));

const money = (amount: number) => ({ amount, currency: 'VND', formatted: `${amount} ₫` });

function mockFetch(body: unknown, status = 200) {
  const fn = jest.fn(async () => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
  global.fetch = fn as unknown as typeof fetch;
  return fn;
}

test('gửi token, query và biến; trả data', async () => {
  const fetchMock = mockFetch({
    data: { myCart: { id: 'c1', itemCount: 0, checkoutReady: true, subtotal: money(0), lines: [] } },
  });
  const data = await gql(MyCartQuery);
  expect(data.myCart.id).toBe('c1');
  const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
  expect(url).toMatch(/\/graphql$/);
  expect((init.headers as Record<string, string>).Authorization).toBe('Bearer tok');
  expect(JSON.parse(init.body as string).query).toContain('myCart');
});

test('lỗi GraphQL thành GraphQLRequestError có code', async () => {
  mockFetch({ data: null, errors: [{ message: 'login required', extensions: { code: 'UNAUTHENTICATED' } }] });
  await expect(gql(MyCartQuery)).rejects.toMatchObject({ code: 'UNAUTHENTICATED' });
  await expect(gql(MyCartQuery)).rejects.toBeInstanceOf(GraphQLRequestError);
});

test('api cũ nhận dữ liệu GraphQL đã chuyển sang model cũ', async () => {
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
  const res = await getAllProduct({ page: 1, limit: 2 }, { category: ['cat-1'] });
  expect(res.data.totalPage).toBe(2);
  expect(res.data.responseList[0]).toMatchObject({ id: 'p1', slug: 'ao', thumbnail: { imagePath: 'https://cdn/x.jpg' } });
  expect(res.data.responseList[0].price?.formatted).toBe('100 ₫');

  mockFetch({
    data: {
      myCart: {
        id: 'c1',
        itemCount: 1,
        checkoutReady: true,
        subtotal: money(100),
        lines: [
          {
            id: 'l1',
            quantity: 1,
            purchasable: true,
            issue: null,
            unitPrice: money(100),
            lineTotal: money(100),
            product: { id: 'p1', name: 'Áo', slug: 'ao', thumbnail: null },
          },
        ],
      },
    },
  });
  const carts = await getCartByCustomerId('u1');
  expect(carts.data).toHaveLength(1);
  expect(carts.data[0].cartItems[0]).toMatchObject({ id: 'l1', quantity: 1, product: { name: 'Áo' } });
});
