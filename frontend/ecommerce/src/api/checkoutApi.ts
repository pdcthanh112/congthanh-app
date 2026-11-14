import { gql } from '@/graphql/client';
import { CheckoutMutation, CheckoutProgressQuery } from '@/graphql/documents';
import type { CheckoutInput } from '@/graphql/types';

/**
 * Đặt hàng từ giỏ hiện tại (saga Temporal ở order-service). Kết quả có orderCode để poll checkoutProgress;
 * với cổng thanh toán online, chuyển khách tới checkoutUrl / paymentRedirectUrl.
 *
 * idempotencyKey phải do caller giữ ổn định cho một lần đặt hàng (kể cả khi bấm lại hoặc retry mạng),
 * để backend không tạo hai đơn cho cùng một lần thanh toán.
 */
export const checkout = async (input: CheckoutInput) => (await gql(CheckoutMutation, { input })).checkout;

export const getCheckoutProgress = async (orderCode: string) =>
  (await gql(CheckoutProgressQuery, { orderCode })).checkoutProgress;
