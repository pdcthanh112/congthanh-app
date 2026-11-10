import type { CartView, CheckoutBody, ProductSummary, StockLevel } from '@ecom/clients';
import { DomainError } from '@ecom/platform';
import { vnd } from '@ecom/shared';
import type { Backends } from '../src/app.module.js';

/** Backend giả trong bộ nhớ: đủ hành vi để kiểm tra điều phối của BFF mà không cần service thật. */
export function fakeBackends() {
  const products = new Map<string, ProductSummary>([
    ['p1', summary('p1', 'Áo thun', 150_000, 200_000)],
    ['p2', summary('p2', 'Quần jean', 450_000, 450_000)],
    ['p-off', { ...summary('p-off', 'Ngừng bán', 10_000, 10_000), active: false }],
  ]);
  const stock = new Map<string, StockLevel>([
    ['p1', level('p1', 10)],
    ['p2', level('p2', 1)],
  ]);
  const carts = new Map<string, CartView>();
  let itemSeq = 0;
  const calls = { byIds: 0, levels: 0, detail: 0, checkout: [] as { body: CheckoutBody; key: string }[] };

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
        if (code !== 'GIAM10') return { valid: false, discount: null, reason: 'code not found', promotionId: null };
        return {
          valid: true,
          discount: { ...subtotal, amountMinor: Math.round(subtotal.amountMinor * 0.1) },
          reason: null,
          promotionId: '1',
        };
      },
    },
    orders: {
      async checkout(_t, body, key) {
        calls.checkout.push({ body, key });
        const money = { amount: 300_000, currency: 'VND' };
        return {
          orderId: 'o-1',
          orderCode: 'ORD-1',
          status: 'PENDING',
          total: money,
          discount: { amount: 0, currency: 'VND' },
          checkoutUrl: '/api/ecommerce/orders/ORD-1/checkout',
        };
      },
      async get(_t, code) {
        if (code !== 'ORD-1') throw DomainError.notFound('order');
        const m = (amount: number) => ({ amount, currency: 'VND' });
        return {
          orderId: 'o-1',
          orderCode: 'ORD-1',
          status: 'CONFIRMED',
          lines: [
            { productId: 'p1', sku: 'SKU-p1', name: 'Áo thun', quantity: 2, unitPrice: m(150_000), total: m(300_000) },
          ],
          subtotal: m(300_000),
          discount: m(0),
          total: m(300_000),
          placedAt: new Date('2026-11-01T00:00:00Z'),
        };
      },
      async history() {
        return [
          {
            orderCode: 'ORD-1',
            status: 'CONFIRMED',
            total: { amount: '300000.00', currency: 'VND' },
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
    addresses: {
      async shippingAddress(customerId, addressId) {
        if (addressId === 'missing') throw DomainError.notFound('address');
        return {
          $typeName: 'ecommerce.customer.v1.ShippingAddress',
          id: addressId ?? 'a-default',
          customerId,
          fullName: 'Nguyễn Văn A',
          phoneNumber: '0901234567',
          label: 'HOME',
          countryCode: 'VN',
          provinceCode: '79',
          districtCode: '760',
          wardCode: '26734',
          addressLine: '1 Lê Lợi',
          postalCode: '',
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

  return { backends, products, stock, carts, calls };
}

function summary(id: string, name: string, price: number, original: number): ProductSummary {
  return {
    id,
    sku: `SKU-${id}`,
    name,
    price: vnd(price),
    originalPrice: vnd(original),
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
