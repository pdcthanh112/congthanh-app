import React from 'react';
import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import Banner from '@/components/Banner';
import { makeServerQueryClient } from '@/lib/query-client';
import type { Locale } from '@/lib/locales';
import { homeProductsQuery } from './queries';
import ShowRecentSearchProduct from './ShowRecentSearchProduct';

export default async function HomePage({ params }: { params: Promise<{ locale: Locale }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations('home');

  // Render sẵn trang sản phẩm đầu tiên ở server, client nhận lại qua HydrationBoundary (không fetch lại).
  const queryClient = makeServerQueryClient();
  await queryClient.prefetchQuery(homeProductsQuery(1));

  return (
    <React.Fragment>
      <Banner />
      <section className="mx-auto w-full max-w-7xl px-4 py-6">
        <h2 className="mb-4 text-xl font-semibold">{t('recent_products')}</h2>
        <HydrationBoundary state={dehydrate(queryClient)}>
          <ShowRecentSearchProduct />
        </HydrationBoundary>
      </section>
    </React.Fragment>
  );
}
