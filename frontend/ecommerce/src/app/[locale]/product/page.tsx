import React from 'react';
import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { getAllCategoryJson } from '@/api/categoryApi';
import { getAllProduct } from '@/api/productApi';
import BreadcrumbComponent from '@/components/Breadcrumb';
import { parseProductFilter, PRODUCT_PAGE_SIZE, productListKey } from '@/features/product/listing/filters';
import ProductListing from '@/features/product/listing/ProductListing';
import type { Locale } from '@/lib/locales';
import { makeServerQueryClient } from '@/lib/query-client';
import { CATEGORY_KEY } from '@/utils/constants/queryKey';

type Props = {
  params: Promise<{ locale: Locale }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function ProductPage({ params, searchParams }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations('common');
  const filter = parseProductFilter(await searchParams);

  // Render sẵn danh mục và trang đầu ở server; client tiếp tục bằng useInfiniteQuery với cùng key.
  const queryClient = makeServerQueryClient();
  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: [CATEGORY_KEY, 'tree'],
      queryFn: async () => (await getAllCategoryJson()).data,
    }),
    queryClient.prefetchInfiniteQuery({
      queryKey: productListKey(filter),
      queryFn: async ({ pageParam }) =>
        (await getAllProduct({ page: pageParam, limit: PRODUCT_PAGE_SIZE }, filter)).data,
      initialPageParam: 1,
    }),
  ]);

  return (
    <React.Fragment>
      <BreadcrumbComponent
        items={[
          { pageName: t('home'), url: '/home' },
          { pageName: t('product'), url: '' },
        ]}
      />
      <HydrationBoundary state={dehydrate(queryClient)}>
        <ProductListing />
      </HydrationBoundary>
    </React.Fragment>
  );
}
