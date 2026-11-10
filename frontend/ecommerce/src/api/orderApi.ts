import { gql } from '@/graphql/client';
import { CancelOrderMutation, MyOrdersQuery, OrderQuery } from '@/graphql/documents';
import { ok, paged, toOrderDetail } from '@/graphql/legacy';
import { Order, OrderDetail } from '@/models/types';

// Đơn hàng (order-service qua storefront-bff). status 'ALL' hoặc rỗng = mọi trạng thái; page tính từ 0.
export const getOrderByStatus = async (status: string, page: number, limit: number) => {
  const { myOrders } = await gql(MyOrdersQuery, { limit: 100 });
  const filtered = !status || status === 'ALL' ? myOrders : myOrders.filter((o) => o.status === status);
  const items: Order[] = filtered.slice(page * limit, (page + 1) * limit).map((o) => ({
    id: o.orderCode as unknown as number, // model cũ: id number; giờ là mã đơn
    customer: '',
    total: o.total.amount,
    orderDate: Date.parse(o.placedAt),
    status: o.status,
  }));
  return paged(items, page, limit, filtered.length);
};

export const getOrderDetail = async (orderCode: string): Promise<OrderDetail> =>
  toOrderDetail((await gql(OrderQuery, { orderCode })).order);

export const cancelOrder = async (orderCode: string, reason?: string) =>
  ok((await gql(CancelOrderMutation, { orderCode, reason })).cancelOrder);
