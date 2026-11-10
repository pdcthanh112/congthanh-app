import type { CheckoutAccepted, OrderDto, OrderSummaryDto, StockLevel } from '@ecom/clients';
import { formatMoney, fromDecimal, type Money, toDecimal } from '@ecom/shared';
import type { CartSnapshot } from '../../../application/storefront.service.js';
import type {
  CartModel,
  CheckoutResultModel,
  MoneyModel,
  OrderModel,
  OrderSummaryModel,
  StockModel,
  StockStatusGql,
} from './types.js';

export function money(m: Money): MoneyModel {
  return { amount: toDecimal(m), currency: m.currency, formatted: formatMoney(m) };
}

/** Tiền dạng thập phân từ REST của service Java ({amount, currency}). */
export function decimalMoney(m: { amount: number | string; currency: string }): MoneyModel {
  return money(fromDecimal(m.amount, m.currency));
}

export function stock(l: StockLevel): StockModel {
  return { status: l.status as StockStatusGql, available: Math.max(0, l.available) };
}

export function discountPercent(price: Money, original: Money): number {
  if (original.amountMinor <= 0 || price.amountMinor >= original.amountMinor) return 0;
  return Math.round((1 - price.amountMinor / original.amountMinor) * 100);
}

export function cart(c: CartSnapshot): CartModel {
  return {
    id: c.id,
    lines: c.lines.map((l) => ({
      id: l.id,
      product: l.product ? { id: l.productId } : null,
      quantity: l.quantity,
      unitPrice: l.unitPrice ? money(l.unitPrice) : null,
      lineTotal: l.lineTotal ? money(l.lineTotal) : null,
      purchasable: l.purchasable,
      issue: l.issue as CartModel['lines'][number]['issue'],
      addedAt: l.addedAt,
    })),
    subtotal: money(c.subtotal),
    itemCount: c.itemCount,
    checkoutReady: c.checkoutReady,
  };
}

export function checkoutResult(a: CheckoutAccepted): CheckoutResultModel {
  return { ...a, total: decimalMoney(a.total), discount: decimalMoney(a.discount) };
}

export function order(o: OrderDto): OrderModel {
  return {
    orderId: o.orderId,
    orderCode: o.orderCode,
    status: o.status,
    lines: o.lines.map((l) => ({
      product: { id: l.productId },
      name: l.name,
      sku: l.sku ?? null,
      quantity: l.quantity,
      unitPrice: decimalMoney(l.unitPrice),
      total: decimalMoney(l.total),
    })),
    subtotal: decimalMoney(o.subtotal),
    discount: decimalMoney(o.discount),
    total: decimalMoney(o.total),
    promotionCode: o.promotionCode ?? null,
    paymentMethod: o.paymentMethod ?? null,
    shippingAddress: o.shippingAddress
      ? {
          ...o.shippingAddress,
          line2: o.shippingAddress.line2 ?? null,
          ward: o.shippingAddress.ward ?? null,
          district: o.shippingAddress.district ?? null,
          postalCode: o.shippingAddress.postalCode ?? null,
        }
      : null,
    note: o.note ?? null,
    cancelReason: o.cancelReason ?? null,
    placedAt: o.placedAt,
    updatedAt: o.updatedAt ?? null,
  };
}

export function orderSummary(o: OrderSummaryDto): OrderSummaryModel {
  return { ...o, total: decimalMoney(o.total) };
}
