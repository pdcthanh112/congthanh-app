'use client';

import { getCheckoutProgress } from '@/api/checkoutApi';
import { useQuery } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

const FINAL = ['PLACED', 'PREPARING', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'FAILED'];

export default function CheckoutProgress({ orderCode }: { orderCode: string }) {
  const t = useTranslations('checkout');
  const { data } = useQuery({
    queryKey: ['checkout-progress', orderCode],
    queryFn: () => getCheckoutProgress(orderCode),
    refetchInterval: (q) => (q.state.data && FINAL.includes(q.state.data.orderStatus) ? false : 3000),
  });

  if (!data) return null;
  if (data.orderStatus === 'FAILED' || data.failureReason)
    return (
      <p role="alert" className="rounded bg-red-50 p-3 text-red-700">
        {t('payment_failed')}
        {data.failureReason ? `: ${data.failureReason}` : ''}
      </p>
    );
  if (data.paymentRedirectUrl && !FINAL.includes(data.orderStatus))
    return (
      <p>
        {t('payment_pending')}{' '}
        <a href={data.paymentRedirectUrl} className="underline">
          {t('continue_payment')}
        </a>
      </p>
    );
  if (!FINAL.includes(data.orderStatus)) return <p aria-live="polite">{t('processing')}</p>;
  return null;
}
