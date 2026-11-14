import React from 'react';
import Image from 'next/image';
import { notFound } from 'next/navigation';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { CANCELLABLE_STATUSES, getOrder } from '@/api/orderApi';
import { Card, Separator } from '@/components/ui';
import CancelOrderDialog from '@/features/order/CancelOrderDialog';
import OrderStatusBadge from '@/features/order/OrderStatusBadge';
import { Link } from '@/i18n/routing';
import type { Locale } from '@/lib/locales';
import { IMAGE } from '@/utils/constants/path';
import { formatDateTime } from '@/utils/date';

type Props = { params: Promise<{ locale: Locale; orderNumber: string }> };

export default async function OrderDetailPage({ params }: Props) {
  const { locale, orderNumber } = await params;
  setRequestLocale(locale);
  const t = await getTranslations('order');

  // Gọi ở server bằng token của phiên (đọc từ cookie httpOnly), không lộ token xuống trình duyệt.
  const order = await getOrder(decodeURIComponent(orderNumber)).catch(() => null);
  if (!order) notFound();

  const address = order.shippingAddress;

  return (
    <Card className="mx-auto my-6 w-full max-w-5xl space-y-5 p-4 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link href="/order" className="text-sm hover:underline">
          ← {t('back_to_orders')}
        </Link>
        <div className="flex items-center gap-3">
          <h1 className="font-semibold">
            {t('order_code')}: <span className="opacity-80">{order.orderCode}</span>
          </h1>
          <OrderStatusBadge status={order.status} />
        </div>
      </div>

      <div className="text-sm text-muted-foreground">
        {t('placed_at')}: {formatDateTime(order.placedAt, locale)}
        {order.paymentMethod && (
          <>
            {' · '}
            {t('payment_method')}: {order.paymentMethod}
          </>
        )}
      </div>

      {order.cancelReason && (
        <p className="rounded bg-red-50 p-3 text-sm text-red-700">
          {t('cancel_reason')}: {order.cancelReason}
        </p>
      )}

      <ul className="divide-y">
        {order.lines.map((line, i) => (
          <li key={`${line.sku ?? line.name}-${i}`} className="flex items-center gap-3 py-3">
            <span className="relative h-16 w-16 shrink-0 overflow-hidden rounded">
              <Image
                src={line.product?.thumbnail || IMAGE.defaultImage}
                alt={line.name}
                fill
                sizes="64px"
                className="object-cover"
              />
            </span>
            <div className="min-w-0 flex-1">
              {line.product?.slug ? (
                <Link href={`/product/${line.product.slug}`} className="line-clamp-2 hover:underline">
                  {line.name}
                </Link>
              ) : (
                <span className="line-clamp-2">{line.name}</span>
              )}
              <div className="text-sm text-muted-foreground">
                {line.unitPrice.formatted} × {line.quantity}
              </div>
            </div>
            <span className="font-medium">{line.total.formatted}</span>
          </li>
        ))}
      </ul>

      <Separator />

      <div className="grid gap-6 md:grid-cols-2">
        {address && (
          <section>
            <h2 className="mb-2 font-semibold">{t('shipping_address')}</h2>
            <p>
              {address.fullName} · {address.phone}
            </p>
            <p className="text-sm text-muted-foreground">
              {[address.line1, address.line2, address.ward, address.district, address.city, address.country]
                .filter(Boolean)
                .join(', ')}
            </p>
            {order.note && <p className="mt-2 text-sm italic">{order.note}</p>}
          </section>
        )}
        <section className="space-y-1 md:text-right">
          <div>
            {t('subtotal')}: {order.subtotal.formatted}
          </div>
          {order.discount.amount > 0 && (
            <div>
              {t('discount')}
              {order.promotionCode && ` (${order.promotionCode})`}: -{order.discount.formatted}
            </div>
          )}
          <div className="text-lg font-semibold">
            {t('total')}: {order.total.formatted}
          </div>
        </section>
      </div>

      {CANCELLABLE_STATUSES.includes(order.status) && (
        <div className="flex justify-end">
          <CancelOrderDialog orderCode={order.orderCode} />
        </div>
      )}
    </Card>
  );
}
