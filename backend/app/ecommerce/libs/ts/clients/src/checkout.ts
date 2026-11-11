import { RestClient } from '@ecommerce/platform';
import { z } from 'zod';
import type { OrderAddress } from './order.js';

const MoneyDto = z.object({ amount: z.union([z.number(), z.string()]), currency: z.string() });
const Line = z.object({
  productId: z.string(),
  sku: z.string().nullish(),
  name: z.string().nullish(),
  quantity: z.number(),
  unitPrice: MoneyDto.nullish(),
  compareAt: MoneyDto.nullish(),
  lineTotal: MoneyDto.nullish(),
});
const ShippingOption = z.object({
  carrier: z.string(),
  serviceCode: z.string(),
  serviceName: z.string().nullish(),
  fee: MoneyDto,
  estimatedDaysMin: z.number(),
  estimatedDaysMax: z.number(),
  selected: z.boolean(),
});
const Session = z.object({
  id: z.string(),
  /** OPEN | PENDING_REVIEW | PLACED | REJECTED | EXPIRED | CANCELLED */
  status: z.string(),
  currency: z.string(),
  lines: z.array(Line),
  unpricedProductIds: z.array(z.string()).nullish(),
  promotionCode: z.string().nullish(),
  promotionError: z.string().nullish(),
  shippingOptions: z.array(ShippingOption).nullish(),
  totals: z.object({
    subtotal: MoneyDto,
    discount: MoneyDto,
    shippingFee: MoneyDto,
    tax: MoneyDto,
    total: MoneyDto,
  }),
  orderId: z.string().nullish(),
  orderCode: z.string().nullish(),
  failureReason: z.string().nullish(),
  expiresAt: z.coerce.date().nullish(),
  progress: z
    .object({
      step: z.string().nullish(),
      paymentRedirectUrl: z.string().nullish(),
      failureReason: z.string().nullish(),
    })
    .nullish(),
});
export type CheckoutSessionDto = z.infer<typeof Session>;

export interface OpenSessionBody {
  items: { productId: string; quantity: number }[];
  currency?: string;
  promotionCode?: string | null;
  shippingAddress?: OrderAddress | null;
  billingAddress?: OrderAddress | null;
  note?: string | null;
}

export interface PlaceBody {
  paymentMethod: string;
  paymentToken?: string | null;
  returnUrl?: string | null;
  deviceId?: string | null;
  /** Tổng khách đang thấy; checkout-service trả 409 PRICE_CHANGED nếu tổng mới khác. */
  expectedTotal?: number | string | null;
}

/**
 * checkout-service (Java) qua REST: phiên checkout báo giá bằng pricing/promotion/shipping/tax, đặt đơn qua
 * fraud-service rồi order-service. Mở phiên và đặt đơn đều nhận Idempotency-Key để thử lại an toàn.
 */
export class CheckoutClient {
  private readonly rest: RestClient;

  constructor(baseUrl: string) {
    this.rest = new RestClient({ baseUrl, name: 'checkout-service', timeoutMs: 8_000 });
  }

  open(token: string, body: OpenSessionBody, idempotencyKey?: string) {
    return this.rest.call('/api/ecommerce/checkout/sessions', Session, {
      method: 'POST',
      body,
      token,
      ...(idempotencyKey ? { idempotent: true, headers: { 'idempotency-key': idempotencyKey } } : {}),
    });
  }

  get(token: string, sessionId: string) {
    return this.rest.call(`/api/ecommerce/checkout/sessions/${encodeURIComponent(sessionId)}`, Session, { token });
  }

  /** 201 = đã tạo đơn, 202 = giữ lại chờ duyệt tay (status PENDING_REVIEW). */
  place(token: string, sessionId: string, body: PlaceBody, idempotencyKey: string, clientIp?: string) {
    return this.rest.call(`/api/ecommerce/checkout/sessions/${encodeURIComponent(sessionId)}/place`, Session, {
      method: 'POST',
      body,
      token,
      idempotent: true,
      headers: { 'idempotency-key': idempotencyKey, ...(clientIp ? { 'x-forwarded-for': clientIp } : {}) },
    });
  }
}
