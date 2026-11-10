import { gql } from '@/graphql/client';
import { MarkNotificationsReadMutation, MyNotificationsQuery } from '@/graphql/documents';
import { ok, toNotification } from '@/graphql/legacy';
import { Notification, Response } from '@/models/types';

// Thông báo in-app (notification-service, qua supergraph). Thông báo mới đẩy qua subscription notificationAdded.
export const getNotificationByCustomer = async (customerId: string): Promise<Response<Notification[]>> => {
  const { myNotifications } = await gql(MyNotificationsQuery, { page: 1, size: 50 });
  return ok(myNotifications.items.map((n) => toNotification(n, customerId)));
};

export const getUnreadNotificationCount = async (): Promise<number> =>
  (await gql(MyNotificationsQuery, { page: 1, size: 1, unreadOnly: true })).unreadNotificationCount;

/** API mới chỉ hỗ trợ đánh dấu đã đọc; status=false (đánh dấu chưa đọc) bị bỏ qua. */
export const changeNotificationReadingStatus = async (notificationId: string | number, status: boolean) =>
  ok(status ? (await gql(MarkNotificationsReadMutation, { ids: [String(notificationId)] })).markNotificationsRead : 0);

export const markAllNotificationsRead = async () =>
  ok((await gql(MarkNotificationsReadMutation, { ids: null })).markNotificationsRead);
