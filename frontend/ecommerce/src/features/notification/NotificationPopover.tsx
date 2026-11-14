'use client';

import { NotificationIcon } from '@/assets/icons';
import { Popover, PopoverContent, PopoverTrigger, Skeleton } from '@/components/ui';
import { Link } from '@/i18n/routing';
import { PATH } from '@/utils/constants/path';
import { useSession } from 'next-auth/react';
import { useTranslations } from 'next-intl';
import { useMarkAllNotificationsRead, useMyNotifications } from './hooks';
import NotificationItem from './NotificationItem';

export default function NotificationPopover() {
  const t = useTranslations();
  const { status } = useSession();
  const { data, isLoading } = useMyNotifications();
  const { mutate: markAll } = useMarkAllNotificationsRead();
  if (status !== 'authenticated') return null;

  const unread = data?.unread ?? 0;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="relative"
          aria-label={
            unread > 0
              ? `${t('notification.title')}: ${t('notification.unread_count', { count: unread })}`
              : t('notification.title')
          }
        >
          <NotificationIcon width={30} height={30} aria-hidden />
          {unread > 0 && (
            <span className="absolute -top-1 -right-1 min-w-4 h-4 px-1 bg-yellow-400 text-[10px] leading-4 text-center rounded-full text-black font-bold">
              {unread}
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(25rem,calc(100vw-2rem))] p-0">
        <div className="flex items-center justify-between px-3 py-2 border-b">
          <h2 className="font-semibold">{t('notification.title')}</h2>
          <div className="flex gap-3 text-sm">
            {unread > 0 && (
              <button type="button" className="hover:underline" onClick={() => markAll()}>
                {t('notification.mark_all_read')}
              </button>
            )}
            <Link href={PATH.NOTIFICATION_PATH_URL.NOTIFICATION} className="hover:underline text-yellow-700">
              {t('common.see_all')}
            </Link>
          </div>
        </div>
        <div className="max-h-96 overflow-y-auto divide-y">
          {isLoading &&
            Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="px-4 py-2">
                <Skeleton className="h-14 w-full" />
              </div>
            ))}
          {!isLoading && (data?.items.length ?? 0) === 0 && (
            <p className="px-4 py-6 text-center">{t('notification.empty')}</p>
          )}
          {data?.items.slice(0, 10).map((item) => (
            <NotificationItem key={item.id} item={item} />
          ))}
        </div>
      </PopoverContent>
    </Popover>
  );
}
