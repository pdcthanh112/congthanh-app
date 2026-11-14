import React, { cache } from 'react';
import { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import {
  getProductDetailBySlug,
  getProductOptionValueByProductId,
  getProductOptionValues,
  getProductVariationsByParentId,
} from '@/api/productApi';
import BreadcrumbComponent from '@/components/Breadcrumb';
import ProductDetail from '@/features/product/component/product-detail';
import RelatedProduct from '@/features/product/component/RelatedProduct';
import { redirect } from '@/i18n/routing';
import type { Locale } from '@/lib/locales';
import { sanitizeRichText } from '@/lib/sanitize';
import { Breadcrumb, ProductOptions, ProductVariant } from '@/models/types';
import { ReviewStatistic } from '@/models/types/Review';

type Params = Promise<{ slug: string; locale: Locale }>;
type SearchParams = Promise<{ [key: string]: string | string[] | undefined }>;

const MOCK_REVIEW_STATISTIC: ReviewStatistic = {
  totalReview: 4646,
  averageRating: 4.7,
  reviewRating5Star: 868,
  reviewRating4Star: 615,
  reviewRating3Star: 486,
  reviewRating2Star: 15,
  reviewRating1Star: 49,
  reviewWithMedia: 1684,
};

/**
 * Dùng chung cho generateMetadata và page trong cùng một request (React.cache) để không gọi API hai lần.
 * Mô tả được sanitize ngay ở server trước khi đưa xuống client component.
 */
const fetchProduct = cache(async (slug: string) => {
  try {
    const product = (await getProductDetailBySlug(slug)).data;
    return { ...product, description: sanitizeRichText(product.description) };
  } catch {
    return null;
  }
});

const fetchAndSortProductVariations = async (productId: string): Promise<ProductVariant[]> => {
  try {
    const variations = await getProductVariationsByParentId(productId);
    return [...variations].sort((a, b) => Object.keys(a.options).length - Object.keys(b.options).length);
  } catch {
    return [];
  }
};

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { slug } = await params;
  const product = await fetchProduct(slug);
  if (!product) notFound();

  return {
    title: product.name,
    description: product.metadata.description,
    openGraph: {
      title: product.name,
      description: product.metadata.description,
      images: product.thumbnail.imagePath ? [{ url: product.thumbnail.imagePath }] : [],
    },
    alternates: {
      canonical: process.env.NEXT_PUBLIC_API_URL + '/product/' + slug,
    },
  };
}

export default async function ProductDetailPage({ params, searchParams }: { params: Params; searchParams: SearchParams }) {
  const { slug, locale } = await params;
  setRequestLocale(locale);
  const { pvid } = await searchParams;
  const t = await getTranslations();

  const product = await fetchProduct(slug);
  if (!product) notFound();
  if (product.parent) redirect({ href: { pathname: `/product/${product.parent}`, query: { pvid: product.id } }, locale });

  const productOptions: ProductOptions[] = [];
  let productVariant: ProductVariant[] = [];

  if (product.hasVariant) {
    const optionValues = await getProductOptionValues(product.id).catch(() => []);
    for (const v of optionValues) {
      const existing = productOptions.find((o) => o.name === v.productOptionName);
      if (existing) existing.value.push(v.productOptionValue);
      else productOptions.push({ id: v.productOptionId, name: v.productOptionName, value: [v.productOptionValue] });
    }
    productVariant = await fetchAndSortProductVariations(product.id);
  }

  const productOptionValue = await getProductOptionValueByProductId(product.id);

  const crumb: Breadcrumb[] = [
    { pageName: t('common.home'), url: '/home' },
    { pageName: t('common.product'), url: '/product' },
    { pageName: product.name, url: '' },
  ];

  return (
    <React.Fragment>
      <BreadcrumbComponent items={crumb} />
      <ProductDetail
        product={product}
        pvid={typeof pvid === 'string' ? pvid : null}
        reviewStatistic={MOCK_REVIEW_STATISTIC}
        productOption={productOptions}
        productVariant={productVariant}
        productOptionValueGet={productOptionValue}
      />
      <RelatedProduct />
    </React.Fragment>
  );
}
