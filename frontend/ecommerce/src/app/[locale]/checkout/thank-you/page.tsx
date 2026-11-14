import { getTranslations, setRequestLocale } from 'next-intl/server';
import CheckoutProgress from '@/features/checkout/CheckoutProgress';
import { Link } from '@/i18n/routing';
import { PATH } from '@/utils/constants/path';
import type { Locale } from '@/lib/locales';

type Props = {
  params: Promise<{ locale: Locale }>;
  searchParams: Promise<{ order?: string }>;
};

export default async function ThankYouPage({ params, searchParams }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  const { order } = await searchParams;
  const t = await getTranslations();

  return (
    <div className="w-full min-h-[30rem] bg-white flex justify-center items-center px-4">
      <div className="max-w-lg text-center space-y-2">
        <h1 className="text-2xl">{t('checkout.thank_you_title')}</h1>
        {order && <p className="font-medium">{t('checkout.order_code', { code: order })}</p>}
        {order && <CheckoutProgress orderCode={order} />}
        <p>
          {t('checkout.thank_you_track')}&nbsp;
          <Link href={order ? `/order/${encodeURIComponent(order)}` : PATH.ORDER_PATH_URL.ORDER} className="underline">
            {t('checkout.here')}
          </Link>
        </p>
        <Link href={PATH.HOME} className="inline-block underline">
          {t('common.back_to_home')}
        </Link>
      </div>
    </div>
  );
}
