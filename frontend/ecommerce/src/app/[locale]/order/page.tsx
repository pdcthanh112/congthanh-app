import { setRequestLocale } from 'next-intl/server';
import OrderList from '@/features/order/OrderList';
import type { Locale } from '@/lib/locales';

export default async function OrderPage({ params }: { params: Promise<{ locale: Locale }> }) {
  setRequestLocale((await params).locale);
  return <OrderList />;
}
