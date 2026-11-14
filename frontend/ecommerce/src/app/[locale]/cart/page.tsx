import { getTranslations, setRequestLocale } from 'next-intl/server';
import CartView from '@/features/cart/component/CartView';
import type { Locale } from '@/lib/locales';

export default async function CartPage({ params }: { params: Promise<{ locale: Locale }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations('cart');

  return (
    <div className="w-full max-w-6xl mx-auto px-4 py-4">
      <h1 className="px-5 py-2 mb-3 text-2xl sm:text-3xl bg-white rounded">{t('your_cart')}</h1>
      <CartView />
    </div>
  );
}
