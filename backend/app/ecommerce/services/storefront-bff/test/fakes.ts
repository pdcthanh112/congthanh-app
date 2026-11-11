import type {
  CartView,
  CheckoutSessionDto,
  OpenSessionBody,
  PlaceBody,
  ProductSummary,
  StockLevel,
} from '@ecommerce/clients';
import { DomainError } from '@ecommerce/platform';
import { usd } from '@ecommerce/shared';
import type { Backends } from '../src/app.module.js';

/** Backend giả trong bộ nhớ: đủ hành vi để kiểm tra điều phối của BFF mà không cần service thật. */
export function fakeBackends() {
  const products = new Map<string, ProductSummary>([
    ['p1', summary('p1', 'Cotton T-Shirt', 1_500, 2_000)],
    ['p2', summary('p2', 'Slim Jeans', 4_500, 4_500)],
    ['p-off', { ...summary('p-off', 'Ngừng bán', 10_000, 10_000), active: false }],
  ]);
  const stock = new Map<string, StockLevel>([
    ['p1', level('p1', 10)],
    ['p2', level('p2', 1)],
  ]);
  const carts = new Map<string, CartView>();
  let itemSeq = 0;
  const sessions = new Map<string, CheckoutSessionDto>();
  /** Đặt fraudReview = true để giả lập fraud-service giữ đơn lại chờ duyệt. */
  const flags = { fraudReview: false };
  const calls = {
    byIds: 0,
    levels: 0,
    detail: 0,
    opens: [] as { body: OpenSessionBody; key: string | undefined }[],
    places: [] as { sessionId: string; body: PlaceBody; key: string }[],
    tracked: [] as { type: string; productId: string; token: string | undefined; sessionId: string | undefined }[],
  };

  const backends: Backends = {
    products: {
      async byIds(ids) {
        calls.byIds++;
        return new Map(ids.flatMap((id) => (products.has(id) ? [[id, products.get(id) as ProductSummary]] : [])));
      },
      async detail(id) {
        calls.detail++;
        if (!products.has(id)) throw DomainError.notFound(`product ${id}`);
        return {
          id,
          name: products.get(id)?.name ?? '',
          slug: `slug-${id}`,
          brand: 'Coolmate',
          image: [
            { imagePath: `/${id}/2.jpg`, displayOrder: 2 },
            { imagePath: `/${id}/1.jpg`, displayOrder: 1 },
          ],
          category: [{ categoryId: '7', displayOrder: 1 }],
        };
      },
      async bySlug(slug) {
        const id = slug.replace(/^slug-/, '');
        if (!products.has(id)) throw DomainError.notFound(`product ${slug}`);
        return backends.products.detail(id);
      },
      async list(page, limit) {
        const all = [...products.keys()];
        const items = await Promise.all(
          all.slice((page - 1) * limit, page * limit).map((id) => backends.products.detail(id)),
        );
        return {
          paginationInfo: { page, limit, totalPage: Math.ceil(all.length / limit), totalElement: all.length },
          responseList: items,
        };
      },
      async byCategory(_c, page, limit) {
        return backends.products.list(page, limit);
      },
      async search(keyword) {
        const hits = [...products.values()].filter((p) => p.name.toLowerCase().includes(keyword.toLowerCase()));
        return Promise.all(hits.map((p) => backends.products.detail(p.id)));
      },
    },
    inventory: {
      async levels(ids) {
        calls.levels++;
        return new Map(ids.flatMap((id) => (stock.has(id) ? [[id, stock.get(id) as StockLevel]] : [])));
      },
    },
    carts: {
      async defaultCart(customerId) {
        return carts.get(customerId);
      },
      async ensureDefault(customerId) {
        if (!carts.has(customerId)) carts.set(customerId, { id: carts.size + 1, customerId, items: [] });
        return (carts.get(customerId) as CartView).id;
      },
      async addItem(_t, cartId, productId, quantity) {
        const cart = [...carts.values()].find((c) => c.id === cartId) as CartView;
        const existing = cart.items.find((i) => i.productId === productId);
        if (existing) existing.quantity += quantity;
        else cart.items.push({ id: ++itemSeq, productId, quantity, addedAt: new Date() });
        return { id: itemSeq, product: productId, quantity };
      },
      async updateItem(_t, cartId, itemId, quantity) {
        const item = findItem(cartId, itemId);
        item.quantity = quantity;
        return { id: itemId, product: item.productId, quantity };
      },
      async removeItem(_t, cartId, itemId) {
        const cart = [...carts.values()].find((c) => c.id === cartId) as CartView;
        cart.items = cart.items.filter((i) => i.id !== itemId);
      },
      async clear(_t, cartId) {
        const cart = [...carts.values()].find((c) => c.id === cartId) as CartView;
        cart.items = [];
      },
    },
    promotions: {
      async quote(code, _c, subtotal) {
        if (code !== 'SAVE10') return { valid: false, discount: null, reason: 'code not found', promotionId: null };
        return {
          valid: true,
          discount: { ...subtotal, amountMinor: Math.round(subtotal.amountMinor * 0.1) },
          reason: null,
          promotionId: '1',
        };
      },
    },
    orders: {
      async get(_t, code) {
        if (code !== 'ORD-1') throw DomainError.notFound('order');
        const m = (amount: number) => ({ amount, currency: 'USD' });
        return {
          orderId: 'o-1',
          orderCode: 'ORD-1',
          status: 'CONFIRMED',
          lines: [
            { productId: 'p1', sku: 'SKU-p1', name: 'Cotton T-Shirt', quantity: 2, unitPrice: m(15), total: m(30) },
          ],
          subtotal: m(30),
          discount: m(0),
          total: m(30),
          placedAt: new Date('2026-11-01T00:00:00Z'),
        };
      },
      async history() {
        return [
          {
            orderCode: 'ORD-1',
            status: 'CONFIRMED',
            total: { amount: '30.00', currency: 'USD' },
            itemCount: 2,
            placedAt: new Date(),
          },
        ];
      },
      async progress(_t, code) {
        return { orderCode: code, orderStatus: 'PENDING', step: 'RESERVING_STOCK' };
      },
      async cancel() {},
    },
    checkouts: {
      async open(_t, body, key) {
        calls.opens.push({ body, key });
        const prev = [...sessions.values()].find((x) => x.id === `cs-${key}`);
        if (prev) return prev;
        const m = (cents: number) => ({ amount: cents / 100, currency: 'USD' });
        const subtotal = body.items.reduce(
          (n, i) => n + (products.get(i.productId)?.price.amountMinor ?? 0) * i.quantity,
          0,
        );
        const discount = body.promotionCode === 'SAVE10' ? subtotal / 10 : 0;
        const s: CheckoutSessionDto = {
          id: `cs-${key ?? sessions.size}`,
          status: 'OPEN',
          currency: 'USD',
          lines: body.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
          unpricedProductIds: [],
          promotionCode: body.promotionCode ?? null,
          promotionError: body.promotionCode && body.promotionCode !== 'SAVE10' ? 'NOT_FOUND' : null,
          totals: {
            subtotal: m(subtotal),
            discount: m(discount),
            shippingFee: m(500),
            tax: m(0),
            total: m(subtotal - discount + 500),
          },
        };
        sessions.set(s.id, s);
        return s;
      },
      async place(_t, id, body, key) {
        calls.places.push({ sessionId: id, body, key });
        const s = sessions.get(id);
        if (!s) throw DomainError.notFound('session');
        if (body.expectedTotal != null && Number(body.expectedTotal) !== Number(s.totals.total.amount)) {
          throw DomainError.conflict('the total changed', 'PRICE_CHANGED');
        }
        const next = flags.fraudReview
          ? { ...s, status: 'PENDING_REVIEW' }
          : { ...s, status: 'PLACED', orderId: 'o-1', orderCode: 'ORD-1' };
        sessions.set(id, next);
        return next;
      },
      async get(_t, id) {
        const s = sessions.get(id);
        if (!s) throw DomainError.notFound('session');
        return s;
      },
    },
    recommendations: {
      async forProduct(productId, kind, limit) {
        const reason = kind === 'SIMILAR' ? 'SIMILAR' : 'BOUGHT_TOGETHER';
        return [...products.keys()]
          .filter((id) => id !== productId && id !== 'p-off')
          .slice(0, limit)
          .map((id) => ({ productId: id, score: 0.5, reason }));
      },
      async trending(limit) {
        return [{ productId: 'p2', score: 3, reason: 'TRENDING' }].slice(0, limit);
      },
      async forYou(limit, token, sessionId) {
        return [{ productId: 'p1', score: 1, reason: token || sessionId ? 'FOR_YOU' : 'TRENDING' }].slice(0, limit);
      },
      async track(type, productId, token, sessionId) {
        calls.tracked.push({ type, productId, token, sessionId });
      },
    },
    addresses: {
      async shippingAddress(customerId, addressId) {
        if (addressId === 'missing') throw DomainError.notFound('address');
        return {
          $typeName: 'ecommerce.customer.v1.ShippingAddress',
          id: addressId ?? 'a-default',
          customerId,
          fullName: 'Alex Johnson',
          phoneNumber: '+12125550100',
          label: 'HOME',
          countryCode: 'US',
          provinceCode: 'NY',
          districtCode: '',
          wardCode: '',
          addressLine: '350 5th Ave',
          postalCode: '10118',
          latitude: 0,
          longitude: 0,
          isDefault: true,
        };
      },
    },
  };

  function findItem(cartId: number, itemId: number) {
    const cart = [...carts.values()].find((c) => c.id === cartId) as CartView;
    const item = cart.items.find((i) => i.id === itemId);
    if (!item) throw DomainError.notFound('item');
    return item;
  }

  return { backends, products, stock, carts, calls, flags };
}

function summary(id: string, name: string, price: number, original: number): ProductSummary {
  return {
    id,
    sku: `SKU-${id}`,
    name,
    price: usd(price),
    originalPrice: usd(original),
    active: true,
    sellerId: 'seller-1',
  };
}

function level(productId: string, available: number): StockLevel {
  return {
    productId,
    available,
    reserved: 0,
    total: available,
    reorderPoint: 2,
    status: available > 0 ? 'IN_STOCK' : 'OUT_OF_STOCK',
  };
}
