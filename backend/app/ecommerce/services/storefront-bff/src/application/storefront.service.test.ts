import { DomainError } from '@ecommerce/platform';
import { testPrincipal } from '@ecommerce/platform/testing';
import { describe, expect, it } from 'vitest';
import { fakeBackends } from '../../test/fakes.js';
import { StorefrontService } from './storefront.service.js';

function setup() {
  const f = fakeBackends();
  const b = f.backends;
  const svc = new StorefrontService(
    b.products,
    b.inventory,
    b.carts,
    b.promotions,
    b.orders,
    b.addresses,
    b.checkouts,
    b.recommendations,
    'USD',
  );
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
    expect(cart.subtotal).toEqual({ amountMinor: 7_500, currency: 'USD' });
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
    expect(cart.subtotal.amountMinor).toBe(3_000);
    expect(cart.checkoutReady).toBe(false);
    await expect(svc.checkout(me, { idempotencyKey: 'k'.repeat(20), paymentMethod: 'CARD' })).rejects.toMatchObject({
      code: 'CART_NOT_PURCHASABLE',
    });
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
    const q = await svc.quotePromotion(me, ' save10 ');
    expect(q).toMatchObject({ valid: true, discount: { amountMinor: 300 }, total: { amountMinor: 2_700 } });
    expect((await svc.quotePromotion(me, 'NOPE')).valid).toBe(false);
  });

  it('checkout: mở phiên ở checkout-service (giỏ + địa chỉ mặc định) rồi đặt đơn với Idempotency-Key', async () => {
    const { svc, me, calls } = setup();
    await svc.addToCart(me, 'p1', 2);
    const res = await svc.checkout(me, {
      idempotencyKey: 'chk_0123456789abcdef',
      paymentMethod: 'CARD',
      promotionCode: 'save10',
      expectedTotal: 32,
    });
    expect(res).toMatchObject({
      status: 'PLACED',
      orderCode: 'ORD-1',
      checkoutUrl: '/api/ecommerce/orders/ORD-1/checkout',
    });
    expect(calls.opens[0]).toMatchObject({
      key: 'chk_0123456789abcdef-open',
      body: {
        items: [{ productId: 'p1', quantity: 2 }],
        promotionCode: 'SAVE10',
        currency: 'USD',
        shippingAddress: { fullName: 'Alex Johnson', phone: '+12125550100', city: 'NY', country: 'US' },
      },
    });
    expect(calls.places[0]).toMatchObject({
      sessionId: res.checkoutSessionId,
      key: 'chk_0123456789abcdef',
      body: { paymentMethod: 'CARD', expectedTotal: 32 },
    });
    // Gửi lại cùng key (mất mạng): cùng phiên, không tạo đơn thứ hai.
    const again = await svc.checkout(me, {
      idempotencyKey: 'chk_0123456789abcdef',
      paymentMethod: 'CARD',
      promotionCode: 'save10',
    });
    expect(again.checkoutSessionId).toBe(res.checkoutSessionId);
  });

  it('checkout: mã giảm giá sai báo lỗi trước khi đặt; giá đổi báo PRICE_CHANGED', async () => {
    const { svc, me, calls } = setup();
    await svc.addToCart(me, 'p1', 1);
    await expect(
      svc.checkout(me, { idempotencyKey: 'k'.repeat(20), paymentMethod: 'CARD', promotionCode: 'NOPE' }),
    ).rejects.toMatchObject({ code: 'PROMOTION_INVALID' });
    expect(calls.places).toHaveLength(0);
    await expect(
      svc.checkout(me, { idempotencyKey: 'j'.repeat(20), paymentMethod: 'CARD', expectedTotal: 1 }),
    ).rejects.toMatchObject({ code: 'PRICE_CHANGED' });
  });

  it('checkout bị giữ chờ duyệt: chưa có orderCode, theo dõi bằng checkoutSession', async () => {
    const { svc, me, flags } = setup();
    flags.fraudReview = true;
    await svc.addToCart(me, 'p1', 1);
    const res = await svc.checkout(me, { idempotencyKey: 'r'.repeat(20), paymentMethod: 'CARD' });
    expect(res).toMatchObject({ status: 'PENDING_REVIEW', orderCode: null, checkoutUrl: null });
    expect((await svc.checkoutSession(me, res.checkoutSessionId)).status).toBe('PENDING_REVIEW');
  });

  it('gợi ý: giới hạn số lượng, khách vãng lai theo phiên, bỏ qua tín hiệu không định danh', async () => {
    const { svc, me, calls } = setup();
    expect(await svc.recommendationsFor('p1', 'SIMILAR', 500)).toEqual([
      { productId: 'p2', score: 0.5, reason: 'SIMILAR' },
    ]);
    expect((await svc.forYou(undefined, 5, 'sess_12345678'))[0]?.reason).toBe('FOR_YOU');
    expect((await svc.forYou(undefined, 5))[0]?.reason).toBe('TRENDING');
    expect(await svc.trackView(undefined, 'p1')).toBe(false);
    expect(await svc.trackView(me, 'p1', 'sess_ignored')).toBe(true);
    expect(calls.tracked).toEqual([{ type: 'VIEW', productId: 'p1', token: me.token, sessionId: undefined }]);
  });

  it('checkout kiểm tra đầu vào', async () => {
    const { svc, me } = setup();
    await expect(svc.checkout(me, { idempotencyKey: 'short', paymentMethod: 'CARD' })).rejects.toMatchObject({
      kind: 'INVALID',
    });
    await expect(svc.checkout(me, { idempotencyKey: 'k'.repeat(20), paymentMethod: 'PAYPAL' })).rejects.toMatchObject({
      kind: 'INVALID',
    });
    await expect(svc.checkout(me, { idempotencyKey: 'k'.repeat(20), paymentMethod: 'AFFIRM' })).rejects.toMatchObject({
      kind: 'INVALID',
    });
    await expect(svc.checkout(me, { idempotencyKey: 'k'.repeat(20), paymentMethod: 'CARD' })).rejects.toMatchObject({
      code: 'CART_EMPTY',
    });
  });
});
