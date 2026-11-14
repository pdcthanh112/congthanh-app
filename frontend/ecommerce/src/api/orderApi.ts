import { gql } from '@/graphql/client';
import { CancelOrderMutation, MyOrdersQuery, OrderQuery } from '@/graphql/documents';
import type { Order, OrderSummary } from '@/graphql/types';

export type { Order, OrderSummary };

/** Trạng thái còn cho phép khách tự huỷ đơn. */
export const CANCELLABLE_STATUSES = ['PLACED', 'PENDING', 'PREPARING'];

// Đơn hàng (order-service qua storefront-bff). Supergraph trả danh sách gần nhất, lọc trạng thái phía client.
export const getMyOrders = async (limit = 100): Promise<OrderSummary[]> => (await gql(MyOrdersQuery, { limit })).myOrders;

export const getOrder = async (orderCode: string): Promise<Order | null> =>
  (await gql(OrderQuery, { orderCode })).order ?? null;

export const cancelOrder = async (orderCode: string, reason?: string) =>
  (await gql(CancelOrderMutation, { orderCode, reason: reason || null })).cancelOrder;
