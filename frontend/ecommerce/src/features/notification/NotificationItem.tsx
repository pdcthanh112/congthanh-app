'use client';

import React from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { Check } from 'lucide-react';
import type { Notification } from '@/graphql/types';
import { Link } from '@/i18n/routing';
import { cn } from '@/lib/utils';
import { formatRelative } from '@/utils/date';
import { useMarkNotificationRead } from './hooks';

/** Một thông báo: tiêu đề, nội dung, thời gian tương đối, nút đánh dấu đã đọc. */
export default function NotificationItem({ item }: { item: Notification }) {
  const t = useTranslations('notification');
  const locale = useLocale();
  const { mutate: markRead, isPending } = useMarkNotificationRead();
  const unread = item.readAt === null;

  // Link nội bộ ("/order/...") đi qua router có locale; link ngoài mở bình thường.
  const body = (
    <>
      <p className="font-medium truncate" title={item.title}>
        {item.title}
      </p>
      <p className="text-sm line-clamp-2">{item.body}</p>
      <p className="text-xs opacity-70 italic mt-1">{formatRelative(item.createdAt, locale)}</p>
    </>
  );

  return (
    <div className={cn('px-4 py-2 flex gap-2 items-start', unread && 'bg-green-50')}>
      <div className="flex-1 min-w-0">
        {item.link?.startsWith('/') ? (
          <Link href={item.link} className="block hover:underline" onClick={() => unread && markRead(item.id)}>
            {body}
          </Link>
        ) : (
          body
        )}
      </div>
      {unread && (
        <button
          type="button"
          disabled={isPending}
          onClick={() => markRead(item.id)}
          className="p-1 rounded hover:bg-gray-200"
          aria-label={t('mark_read')}
          title={t('mark_read')}
        >
          <Check size={16} aria-hidden />
        </button>
      )}
    </div>
  );
}
