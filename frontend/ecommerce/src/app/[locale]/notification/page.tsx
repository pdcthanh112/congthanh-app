'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { Button, Skeleton } from '@/components/ui';
import { useMarkAllNotificationsRead, useMyNotifications } from '@/features/notification/hooks';
import NotificationItem from '@/features/notification/NotificationItem';

export default function NotificationPage() {
  const t = useTranslations('notification');
  const { data, isLoading } = useMyNotifications();
  const { mutate: markAll, isPending } = useMarkAllNotificationsRead();

  return (
    <div className="w-full max-w-3xl mx-auto px-4 py-4">
      <div className="flex items-center justify-between mb-3">
        <h1 className="text-xl font-semibold">{t('title')}</h1>
        {(data?.unread ?? 0) > 0 && (
          <Button variant="outline" size="sm" disabled={isPending} onClick={() => markAll()}>
            {t('mark_all_read')}
          </Button>
        )}
      </div>
      <div className="bg-white rounded divide-y">
        {isLoading &&
          Array.from({ length: 5 }, (_, i) => (
            <div key={i} className="p-3">
              <Skeleton className="h-16 w-full" />
            </div>
          ))}
        {!isLoading && (data?.items.length ?? 0) === 0 && <p className="p-6 text-center">{t('empty')}</p>}
        {data?.items.map((item) => (
          <NotificationItem key={item.id} item={item} />
        ))}
      </div>
    </div>
  );
}
