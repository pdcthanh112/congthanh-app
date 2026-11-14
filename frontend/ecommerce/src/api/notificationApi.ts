import { gql } from '@/graphql/client';
import { MarkNotificationsReadMutation, MyNotificationsQuery } from '@/graphql/documents';
import type { Notification } from '@/graphql/types';

// Thông báo in-app (notification-service, qua supergraph) của khách đang đăng nhập.
export const getMyNotifications = async (): Promise<{ items: Notification[]; unread: number }> => {
  const { myNotifications, unreadNotificationCount } = await gql(MyNotificationsQuery, { page: 1, size: 50 });
  return { items: myNotifications.items, unread: unreadNotificationCount };
};

export const markNotificationRead = async (id: string) =>
  (await gql(MarkNotificationsReadMutation, { ids: [id] })).markNotificationsRead;

export const markAllNotificationsRead = async () =>
  (await gql(MarkNotificationsReadMutation, { ids: null })).markNotificationsRead;
