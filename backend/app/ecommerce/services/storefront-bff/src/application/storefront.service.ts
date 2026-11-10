import type { DiscountQuote, OrderAddress, ProductSummary, StockLevel } from '@ecom/clients';
import { DomainError, type Principal } from '@ecom/platform';
import { type Money, multiplyMoney, sumMoney } from '@ecom/shared';
import { Inject, Injectable } from '@nestjs/common';
import {
  ADDRESSES,
  type AddressPort,
  CARTS,
  type CartPort,
  CURRENCY,
  INVENTORY,
  type InventoryPort,
  ORDERS,
  type OrderPort,
  PRODUCTS,
  PROMOTIONS,
  type ProductPort,
  type PromotionPort,
} from './ports.js';

export const MAX_QUANTITY = 999;

export interface CartLineView {
  id: number;
  productId: string;
  quantity: number;
  product: ProductSummary | undefined;
  unitPrice: Money | null;
  lineTotal: Money | null;
  /** Sản phẩm còn bán và đủ hàng cho số lượng đang chọn. */
  purchasable: boolean;
  /** Lý do không mua được (hiển thị cho khách). */
  issue: 'UNAVAILABLE' | 'OUT_OF_STOCK' | 'INSUFFICIENT_STOCK' | null;
  addedAt: Date | null;
}

export interface CartSnapshot {
  id: number | null;
  lines: CartLineView[];
  subtotal: Money;
  itemCount: number;
  checkoutReady: boolean;
}

export type PaymentMethod = 'CASH_ON_DELIVERY' | 'CREDIT_CARD' | 'STRIPE' | 'PAYPAL' | 'VN_PAY' | 'MOMO';

export interface CheckoutCommand {
  idempotencyKey: string;
  paymentMethod: PaymentMethod;
  shippingAddressId?: string | null;
  promotionCode?: string | null;
  paymentToken?: string | null;
  returnUrl?: string | null;
  note?: string | null;
  clientIp?: string;
}

/**
 * Điều phối cho storefront: ghép giỏ hàng (cart-service) với giá (product-service) và tồn kho
 * (inventory-service), báo giá khuyến mãi, chuẩn bị lệnh checkout cho order-service.
 * Giá cuối cùng luôn do order-service tính lại; số ở đây chỉ để hiển thị.
 */
@Injectable()
export class StorefrontService {
  constructor(
    @Inject(PRODUCTS) readonly products: ProductPort,
    @Inject(INVENTORY) readonly inventory: InventoryPort,
    @Inject(CARTS) private readonly carts: CartPort,
    @Inject(PROMOTIONS) private readonly promotions: PromotionPort,
    @Inject(ORDERS) readonly orders: OrderPort,
    @Inject(ADDRESSES) private readonly addresses: AddressPort,
    @Inject(CURRENCY) private readonly currency: string,
  ) {}

  async cart(p: Principal): Promise<CartSnapshot> {
    const cart = await this.carts.defaultCart(p.id, p.token);
    if (!cart || cart.items.length === 0) {
      return {
        id: cart?.id ?? null,
        lines: [],
        subtotal: sumMoney([], this.currency),
        itemCount: 0,
        checkoutReady: false,
      };
    }
    const ids = [...new Set(cart.items.map((i) => i.productId))];
    const [products, stock] = await Promise.all([this.products.byIds(ids), this.stockOrEmpty(ids, p.token)]);
    const lines = cart.items.map((i) => toLine(i, products.get(i.productId), stock.get(i.productId)));
    const priced = lines.filter((l) => l.purchasable && l.lineTotal).map((l) => l.lineTotal as Money);
    return {
      id: cart.id,
      lines,
      subtotal: sumMoney(priced, this.currency),
      itemCount: lines.reduce((n, l) => n + l.quantity, 0),
      checkoutReady: lines.length > 0 && lines.every((l) => l.purchasable),
    };
  }

  async addToCart(p: Principal, productId: string, quantity: number): Promise<CartSnapshot> {
    assertQuantity(quantity);
    const product = (await this.products.byIds([productId])).get(productId);
    if (!product?.active) throw DomainError.notFound(`product ${productId} not found`);
    const level = (await this.stockOrEmpty([productId], p.token)).get(productId);
    if (level && level.available < quantity) {
      throw DomainError.conflict(`only ${level.available} left in stock`, 'INSUFFICIENT_STOCK');
    }
    const cartId = await this.carts.ensureDefault(p.id, p.token);
    await this.carts.addItem(p.token, cartId, productId, quantity);
    return this.cart(p);
  }

  async updateItem(p: Principal, itemId: number, quantity: number): Promise<CartSnapshot> {
    if (quantity === 0) return this.removeItem(p, itemId);
    assertQuantity(quantity);
    const cartId = await this.requireCartId(p);
    await this.carts.updateItem(p.token, cartId, itemId, quantity);
    return this.cart(p);
  }

  async removeItem(p: Principal, itemId: number): Promise<CartSnapshot> {
    const cartId = await this.requireCartId(p);
    await this.carts.removeItem(p.token, cartId, itemId);
    return this.cart(p);
  }

  async clearCart(p: Principal): Promise<CartSnapshot> {
    const cart = await this.carts.defaultCart(p.id, p.token);
    if (cart && cart.items.length > 0) await this.carts.clear(p.token, cart.id);
    return this.cart(p);
  }

  /** Báo giá mã giảm giá trên giỏ hiện tại (không giữ lượt dùng; order-service giữ khi đặt). */
  async quotePromotion(p: Principal, code: string): Promise<DiscountQuote & { subtotal: Money; total: Money }> {
    const cart = await this.cart(p);
    if (cart.lines.length === 0) throw DomainError.invalid('cart is empty', 'CART_EMPTY');
    const q = await this.promotions.quote(code.trim().toUpperCase(), p.id, cart.subtotal);
    const discount = q.valid && q.discount ? q.discount : null;
    const total = discount
      ? { ...cart.subtotal, amountMinor: Math.max(0, cart.subtotal.amountMinor - discount.amountMinor) }
      : cart.subtotal;
    return { ...q, discount, subtotal: cart.subtotal, total };
  }

  /**
   * Đặt hàng từ giỏ mặc định. Idempotency-Key do client sinh một lần cho mỗi lần bấm "Đặt hàng":
   * gửi lại (mạng chập chờn) trả về cùng đơn thay vì tạo đơn mới.
   */
  async checkout(p: Principal, cmd: CheckoutCommand) {
    if (!/^[A-Za-z0-9_-]{16,64}$/.test(cmd.idempotencyKey)) {
      throw DomainError.invalid('idempotencyKey must be 16-64 url-safe characters');
    }
    if (cmd.paymentMethod !== 'CASH_ON_DELIVERY' && !cmd.returnUrl) {
      throw DomainError.invalid('returnUrl is required for online payment');
    }
    const cart = await this.cart(p);
    if (cart.lines.length === 0) throw DomainError.invalid('cart is empty', 'CART_EMPTY');
    const blocked = cart.lines.filter((l) => !l.purchasable);
    if (blocked.length > 0) {
      throw DomainError.conflict(
        `some items cannot be purchased: ${blocked.map((l) => l.productId).join(', ')}`,
        'CART_NOT_PURCHASABLE',
      );
    }
    const address = toOrderAddress(await this.addresses.shippingAddress(p.id, cmd.shippingAddressId));
    const accepted = await this.orders.checkout(
      p.token,
      {
        items: cart.lines.map((l) => ({ productId: l.productId, quantity: l.quantity })),
        promotionCode: cmd.promotionCode?.trim().toUpperCase() || null,
        paymentMethod: cmd.paymentMethod,
        paymentToken: cmd.paymentToken ?? null,
        returnUrl: cmd.returnUrl ?? null,
        shippingAddress: address,
        note: cmd.note?.slice(0, 500) ?? null,
      },
      cmd.idempotencyKey,
      cmd.clientIp,
    );
    // Giỏ không bị xoá ở đây: cart-service tự dọn khi nhận OrderPlaced, nên thanh toán thất bại
    // thì khách vẫn còn giỏ để thử lại.
    return accepted;
  }

  /** Tồn kho chỉ để hiển thị: inventory-service lỗi thì vẫn cho xem giỏ (giữ chỗ thật ở saga). */
  private async stockOrEmpty(ids: readonly string[], token: string): Promise<Map<string, StockLevel>> {
    try {
      return await this.inventory.levels(ids, token);
    } catch {
      return new Map();
    }
  }

  private async requireCartId(p: Principal): Promise<number> {
    const cart = await this.carts.defaultCart(p.id, p.token);
    if (!cart) throw DomainError.notFound('cart not found');
    return cart.id;
  }
}

function assertQuantity(q: number) {
  if (!Number.isInteger(q) || q < 1 || q > MAX_QUANTITY) {
    throw DomainError.invalid(`quantity must be an integer between 1 and ${MAX_QUANTITY}`);
  }
}

function toLine(
  item: { id: number; productId: string; quantity: number; addedAt: Date | null },
  product: ProductSummary | undefined,
  stock: StockLevel | undefined,
): CartLineView {
  let issue: CartLineView['issue'] = null;
  if (!product?.active) issue = 'UNAVAILABLE';
  else if (stock && (stock.status === 'OUT_OF_STOCK' || stock.status === 'DISCONTINUED' || stock.available <= 0))
    issue = 'OUT_OF_STOCK';
  else if (stock && stock.available < item.quantity) issue = 'INSUFFICIENT_STOCK';
  return {
    id: item.id,
    productId: item.productId,
    quantity: item.quantity,
    product,
    unitPrice: product?.price ?? null,
    lineTotal: product ? multiplyMoney(product.price, item.quantity) : null,
    purchasable: issue === null,
    issue,
    addedAt: item.addedAt,
  };
}

function toOrderAddress(a: {
  fullName: string;
  phoneNumber: string;
  addressLine: string;
  wardCode: string;
  districtCode: string;
  provinceCode: string;
  countryCode: string;
  postalCode: string;
}): OrderAddress {
  return {
    fullName: a.fullName,
    phone: a.phoneNumber,
    line1: a.addressLine,
    line2: null,
    ward: a.wardCode || null,
    district: a.districtCode || null,
    city: a.provinceCode || a.countryCode,
    country: a.countryCode,
    postalCode: a.postalCode || null,
  };
}
