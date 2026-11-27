'use client';

import { getProductRecommendations } from '@/api/productApi';
import { toProduct } from '@/graphql/legacy';
import { PRODUCT_KEY } from '@/utils/constants/queryKey';
import { useQuery } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import ShowListProduct from '../ShowListProduct';

const RELATED_LIMIT = 10;

export default function RelatedProduct({ productId }: { productId: string }) {
  const t = useTranslations();
  const { data, isPending, isError } = useQuery({
    queryKey: [PRODUCT_KEY, productId, 'related'],
    queryFn: () => getProductRecommendations(productId, 'SIMILAR', RELATED_LIMIT),
    staleTime: 5 * 60_000,
  });

  if (isError || (!isPending && !data?.length)) return null;

  return (
    <section className="w-full max-w-6xl mx-auto px-4 my-3">
      <div className="bg-white p-5">
        <h2 className="bg-yellow-100 px-2 py-1 rounded-sm uppercase mb-4">{t('product.related_products')}</h2>
        <ShowListProduct loading={isPending} skeletonCount={5} listProduct={data?.map((r) => toProduct(r.product))} />
      </div>
    </section>
  );
}
