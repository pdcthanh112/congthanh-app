import type { OrderDto, StockLevel } from '@ecom/clients';
import { formatMoney, fromDecimal, type Money, toDecimal } from '@ecom/shared';
import type { MoneyModel, OrderModel, StockLevelModel, StockStatusGql } from './types.js';

export function money(m: Money): MoneyModel {
  return { amount: toDecimal(m), currency: m.currency, formatted: formatMoney(m) };
}

export function decimalMoney(m: { amount: number | string; currency: string }): MoneyModel {
  return money(fromDecimal(m.amount, m.currency));
}

export function stockLevel(l: StockLevel): StockLevelModel {
  return { ...l, status: l.status as StockStatusGql, needsReorder: l.available <= l.reorderPoint };
}

export function order(o: OrderDto): OrderModel {
  const a = o.shippingAddress;
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
    shippingAddress: a
      ? {
          ...a,
          line2: a.line2 ?? null,
          ward: a.ward ?? null,
          district: a.district ?? null,
          postalCode: a.postalCode ?? null,
        }
      : null,
    note: o.note ?? null,
    cancelReason: o.cancelReason ?? null,
    placedAt: o.placedAt,
    updatedAt: o.updatedAt ?? null,
  };
}
