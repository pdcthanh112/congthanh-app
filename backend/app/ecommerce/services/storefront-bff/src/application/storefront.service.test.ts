import { DomainError } from '@ecom/platform';
import { testPrincipal } from '@ecom/platform/testing';
import { describe, expect, it } from 'vitest';
import { fakeBackends } from '../../test/fakes.js';
import { StorefrontService } from './storefront.service.js';

function setup() {
  const f = fakeBackends();
  const b = f.backends;
  const svc = new StorefrontService(b.products, b.inventory, b.carts, b.promotions, b.orders, b.addresses, 'VND');
  return { ...f, svc, me: testPrincipal('c1') };
}

describe('StorefrontService', () => {
  it('giỏ rỗng khi khách chưa có giỏ', async () => {
    const { svc, me } = setup();
    expect(await svc.cart(me)).toMatchObject({ id: null, lines: [], itemCount: 0, checkoutReady: false });
  });

  it('thêm vào giỏ: tạo giỏ mặc định, tính tạm tính theo giá product-service', async () => {
    const { svc, me } = setup();
    await svc.addToCart(me, 'p1', 2);
    const cart = await svc.addToCart(me, 'p2', 1);
    expect(cart.itemCount).toBe(3);
    expect(cart.subtotal).toEqual({ amountMinor: 750_000, currency: 'VND' });
    expect(cart.checkoutReady).toBe(true);
  });

  it('chặn sản phẩm ngừng bán, không đủ hàng, số lượng sai', async () => {
    const { svc, me } = setup();
    await expect(svc.addToCart(me, 'p-off', 1)).rejects.toMatchObject({ kind: 'NOT_FOUND' });
    await expect(svc.addToCart(me, 'p2', 2)).rejects.toMatchObject({ kind: 'CONFLICT', code: 'INSUFFICIENT_STOCK' });
    await expect(svc.addToCart(me, 'p1', 0)).rejects.toBeInstanceOf(DomainError);
    await expect(svc.addToCart(me, 'p1', 1.5)).rejects.toBeInstanceOf(DomainError);
  });

  it('dòng hết hàng sau khi thêm: không tính vào tạm tính, giỏ chưa sẵn sàng đặt', async () => {
    const { svc, me, stock } = setup();
    await svc.addToCart(me, 'p1', 2);
    await svc.addToCart(me, 'p2', 1);
    stock.set('p2', { productId: 'p2', available: 0, reserved: 0, total: 0, reorderPoint: 2, status: 'OUT_OF_STOCK' });
    const cart = await svc.cart(me);
    expect(cart.lines.find((l) => l.productId === 'p2')).toMatchObject({ purchasable: false, issue: 'OUT_OF_STOCK' });
    expect(cart.subtotal.amountMinor).toBe(300_000);
    expect(cart.checkoutReady).toBe(false);
    await expect(
      svc.checkout(me, { idempotencyKey: 'k'.repeat(20), paymentMethod: 'CASH_ON_DELIVERY' }),
    ).rejects.toMatchObject({ code: 'CART_NOT_PURCHASABLE' });
  });

  it('inventory lỗi vẫn xem được giỏ', async () => {
    const { svc, me, backends } = setup();
    await svc.addToCart(me, 'p1', 1);
    backends.inventory.levels = async () => {
      throw DomainError.unavailable('inventory down');
    };
    expect((await svc.cart(me)).checkoutReady).toBe(true);
  });

  it('cập nhật về 0 thì xoá dòng; xoá hết giỏ', async () => {
    const { svc, me } = setup();
    const c = await svc.addToCart(me, 'p1', 1);
    const itemId = c.lines[0]?.id as number;
    expect((await svc.updateItem(me, itemId, 3)).itemCount).toBe(3);
    expect((await svc.updateItem(me, itemId, 0)).lines).toHaveLength(0);
    await svc.addToCart(me, 'p2', 1);
    expect((await svc.clearCart(me)).lines).toHaveLength(0);
  });

  it('báo giá mã giảm giá trên tạm tính', async () => {
    const { svc, me } = setup();
    await svc.addToCart(me, 'p1', 2);
    const q = await svc.quotePromotion(me, ' giam10 ');
    expect(q).toMatchObject({ valid: true, discount: { amountMinor: 30_000 }, total: { amountMinor: 270_000 } });
    expect((await svc.quotePromotion(me, 'NOPE')).valid).toBe(false);
  });

  it('checkout gửi giỏ + địa chỉ mặc định + Idempotency-Key cho order-service', async () => {
    const { svc, me, calls } = setup();
    await svc.addToCart(me, 'p1', 2);
    const res = await svc.checkout(me, {
      idempotencyKey: 'chk_0123456789abcdef',
      paymentMethod: 'CASH_ON_DELIVERY',
      promotionCode: 'giam10',
    });
    expect(res.orderCode).toBe('ORD-1');
    expect(calls.checkout[0]).toMatchObject({
      key: 'chk_0123456789abcdef',
      body: {
        items: [{ productId: 'p1', quantity: 2 }],
        promotionCode: 'GIAM10',
        paymentMethod: 'CASH_ON_DELIVERY',
        shippingAddress: { fullName: 'Nguyễn Văn A', phone: '0901234567', city: '79', country: 'VN' },
      },
    });
  });

  it('checkout kiểm tra đầu vào', async () => {
    const { svc, me } = setup();
    await expect(
      svc.checkout(me, { idempotencyKey: 'short', paymentMethod: 'CASH_ON_DELIVERY' }),
    ).rejects.toMatchObject({
      kind: 'INVALID',
    });
    await expect(svc.checkout(me, { idempotencyKey: 'k'.repeat(20), paymentMethod: 'VN_PAY' })).rejects.toMatchObject({
      kind: 'INVALID',
    });
    await expect(
      svc.checkout(me, { idempotencyKey: 'k'.repeat(20), paymentMethod: 'CASH_ON_DELIVERY' }),
    ).rejects.toMatchObject({
      code: 'CART_EMPTY',
    });
  });
});
