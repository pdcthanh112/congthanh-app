import { RestClient } from '@ecommerce/platform';
import { z } from 'zod';

const MoneyDto = z.object({ amount: z.union([z.number(), z.string()]), currency: z.string() });
const AddressDto = z.object({
  fullName: z.string(),
  phone: z.string(),
  line1: z.string(),
  line2: z.string().nullish(),
  ward: z.string().nullish(),
  district: z.string().nullish(),
  city: z.string(),
  country: z.string(),
  postalCode: z.string().nullish(),
});
export type OrderAddress = z.infer<typeof AddressDto>;

const OrderLine = z.object({
  productId: z.string(),
  sku: z.string().nullish(),
  name: z.string(),
  quantity: z.number(),
  unitPrice: MoneyDto,
  total: MoneyDto,
});
const Order = z.object({
  orderId: z.string(),
  orderCode: z.string(),
  status: z.string(),
  lines: z.array(OrderLine),
  subtotal: MoneyDto,
  discount: MoneyDto,
  total: MoneyDto,
  promotionCode: z.string().nullish(),
  paymentMethod: z.string().nullish(),
  shippingAddress: AddressDto.nullish(),
  note: z.string().nullish(),
  cancelReason: z.string().nullish(),
  placedAt: z.coerce.date(),
  updatedAt: z.coerce.date().nullish(),
});
export type OrderDto = z.infer<typeof Order>;
const Summary = z.object({
  orderCode: z.string(),
  status: z.string(),
  total: MoneyDto,
  itemCount: z.number(),
  placedAt: z.coerce.date(),
});
export type OrderSummaryDto = z.infer<typeof Summary>;
const Accepted = z.object({
  orderId: z.string(),
  orderCode: z.string(),
  status: z.string(),
  total: MoneyDto,
  discount: MoneyDto,
  checkoutUrl: z.string(),
});
export type CheckoutAccepted = z.infer<typeof Accepted>;
const Progress = z.object({
  orderCode: z.string(),
  orderStatus: z.string(),
  step: z.string().nullish(),
  paymentRedirectUrl: z.string().nullish(),
  failureReason: z.string().nullish(),
});
export type CheckoutProgress = z.infer<typeof Progress>;

export interface CheckoutBody {
  items: { productId: string; quantity: number }[];
  promotionCode?: string | null;
  paymentMethod: string;
  paymentToken?: string | null;
  returnUrl?: string | null;
  shippingAddress: OrderAddress;
  billingAddress?: OrderAddress | null;
  note?: string | null;
}

/** order-service (Java) qua REST; quyền xem đơn do order-service kiểm theo token chuyển tiếp. */
export class OrderClient {
  private readonly rest: RestClient;

  constructor(baseUrl: string) {
    this.rest = new RestClient({ baseUrl, name: 'order-service', timeoutMs: 8_000 });
  }

  /** Idempotency-Key giúp thử lại an toàn (mất mạng giữa chừng không tạo hai đơn). */
  checkout(token: string, body: CheckoutBody, idempotencyKey: string, clientIp?: string) {
    return this.rest.call('/api/ecommerce/checkout', Accepted, {
      method: 'POST',
      body,
      token,
      idempotent: true,
      headers: { 'idempotency-key': idempotencyKey, ...(clientIp ? { 'x-forwarded-for': clientIp } : {}) },
    });
  }

  get(token: string, orderCode: string) {
    return this.rest.call(`/api/ecommerce/orders/${encodeURIComponent(orderCode)}`, Order, { token });
  }

  history(token: string, limit: number) {
    return this.rest.call('/api/ecommerce/orders', z.array(Summary), { token, query: { limit } });
  }

  progress(token: string, orderCode: string) {
    return this.rest.call(`/api/ecommerce/orders/${encodeURIComponent(orderCode)}/checkout`, Progress, { token });
  }

  async cancel(token: string, orderCode: string, reason?: string | null) {
    await this.rest.call(`/api/ecommerce/orders/${encodeURIComponent(orderCode)}/cancel`, z.unknown(), {
      method: 'POST',
      body: { reason: reason ?? null },
      token,
      idempotent: true,
    });
  }
}
