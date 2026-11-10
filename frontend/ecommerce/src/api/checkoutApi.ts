import { gql } from '@/graphql/client';
import { CheckoutMutation, CheckoutProgressQuery } from '@/graphql/documents';
import { ok } from '@/graphql/legacy';
import type { PaymentMethod } from '@/graphql/types';
import { CheckoutForm } from '@/models/types/';

const METHODS: Record<string, PaymentMethod> = {
  cod: 'CASH_ON_DELIVERY',
  cash: 'CASH_ON_DELIVERY',
  paypal: 'PAYPAL',
  stripe: 'STRIPE',
  card: 'CREDIT_CARD',
  momo: 'MOMO',
  vnpay: 'VN_PAY',
};

/**
 * Đặt hàng từ giỏ hiện tại (saga Temporal ở order-service). Kết quả có orderCode để poll checkoutProgress;
 * với cổng thanh toán online, chuyển khách tới checkoutUrl / paymentRedirectUrl.
 */
export const checkout = async (data: CheckoutForm) => {
  const f = data as CheckoutForm & { address?: string | number; payment?: string; voucher?: string; note?: string };
  const { checkout } = await gql(CheckoutMutation, {
    input: {
      idempotencyKey: crypto.randomUUID(),
      paymentMethod: METHODS[String(f.payment ?? 'cod').toLowerCase()] ?? 'CASH_ON_DELIVERY',
      shippingAddressId: f.address != null ? String(f.address) : null,
      promotionCode: f.voucher || null,
      note: f.note || null,
      returnUrl: typeof window !== 'undefined' ? `${window.location.origin}/checkout/result` : null,
    },
  });
  return ok(checkout);
};

export const getCheckoutProgress = async (orderCode: string) =>
  ok((await gql(CheckoutProgressQuery, { orderCode })).checkoutProgress);
