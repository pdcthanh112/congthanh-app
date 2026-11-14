'use client';

import { useState } from 'react';
import Image from 'next/image';
import { useQuery } from '@tanstack/react-query';
import { useLocale, useTranslations } from 'next-intl';
import { getMyOrders } from '@/api/orderApi';
import ListEmpty from '@/assets/images/list-empty.png';
import PaginationComponent from '@/components/common/PaginationComponent';
import { Card, Skeleton } from '@/components/ui';
import { Link } from '@/i18n/routing';
import { cn } from '@/lib/utils';
import { ORDER_KEY } from '@/utils/constants/queryKey';
import { formatDateTime } from '@/utils/date';
import OrderStatusBadge from './OrderStatusBadge';

const TABS = ['ALL', 'PLACED', 'PREPARING', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'RETURNED'];
const PAGE_SIZE = 10;

export default function OrderList() {
  const t = useTranslations('order');
  const locale = useLocale();
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(1);

  const { data: orders = [], isPending } = useQuery({ queryKey: [ORDER_KEY, 'mine'], queryFn: () => getMyOrders() });
  const filtered = status === 'ALL' ? orders : orders.filter((o) => o.status === status);
  const totalPage = Math.ceil(filtered.length / PAGE_SIZE);
  const visible = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <Card className="mx-auto my-6 w-full max-w-5xl p-4 sm:p-6">
      <h1 className="mb-4 text-xl font-semibold">{t('my_orders')}</h1>
      <div role="tablist" className="mb-4 flex gap-1 overflow-x-auto border-b">
        {TABS.map((key) => (
          <button
            key={key}
            role="tab"
            type="button"
            aria-selected={status === key}
            className={cn(
              'whitespace-nowrap px-4 py-2 text-sm',
              status === key ? 'border-b-2 border-yellow-400 font-medium' : 'text-muted-foreground'
            )}
            onClick={() => {
              setStatus(key);
              setPage(1);
            }}
          >
            {key === 'ALL' ? t('all') : t(`status.${key}`)}
          </button>
        ))}
      </div>

      {isPending ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : visible.length === 0 ? (
        <div className="flex flex-col items-center py-16">
          <Image src={ListEmpty} alt="" width={100} />
          <p className="mt-3 text-muted-foreground">{t('empty')}</p>
        </div>
      ) : (
        <ul className="divide-y">
          {visible.map((o) => (
            <li key={o.orderCode}>
              <Link
                href={`/order/${o.orderCode}`}
                className="flex flex-col gap-1 py-3 hover:bg-muted/50 sm:flex-row sm:items-center sm:justify-between"
              >
                <div>
                  <div className="font-medium">#{o.orderCode}</div>
                  <div className="text-sm text-muted-foreground">
                    {formatDateTime(o.placedAt, locale)} · {t('item_count', { count: o.itemCount })}
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <OrderStatusBadge status={o.status} />
                  <span className="font-semibold">{o.total.formatted}</span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <PaginationComponent page={page} totalPage={totalPage} onChange={setPage} />
    </Card>
  );
}
