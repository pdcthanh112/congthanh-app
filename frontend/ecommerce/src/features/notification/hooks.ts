'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSession } from 'next-auth/react';
import { getMyNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/notificationApi';
import { NOTIFICATION_KEY } from '@/utils/constants/queryKey';

/** Thông báo của khách đang đăng nhập (một query dùng chung cho header và trang thông báo). */
export const useMyNotifications = () => {
  const { status } = useSession();
  return useQuery({
    queryKey: [NOTIFICATION_KEY],
    queryFn: getMyNotifications,
    enabled: status === 'authenticated',
    refetchInterval: 60_000,
  });
};

export const useMarkNotificationRead = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [NOTIFICATION_KEY] }),
  });
};

export const useMarkAllNotificationsRead = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => markAllNotificationsRead(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [NOTIFICATION_KEY] }),
  });
};
