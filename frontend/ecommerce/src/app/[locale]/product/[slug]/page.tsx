import React, { cache } from 'react';
import { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { getProductById, getProductDetail } from '@/api/productApi';
import BreadcrumbComponent from '@/components/Breadcrumb';
import ProductDetail from '@/features/product/component/product-detail';
import ReviewProduct from '@/features/product/component/product-detail/ReviewProduct';
import RelatedProduct from '@/features/product/component/RelatedProduct';
import { toReviewStatistic } from '@/graphql/legacy';
import { redirect } from '@/i18n/routing';
import type { Locale } from '@/lib/locales';
import { sanitizeRichText } from '@/lib/sanitize';
import { Breadcrumb } from '@/models/types';

type Params = Promise<{ slug: string; locale: Locale }>;
type SearchParams = Promise<{ [key: string]: string | string[] | undefined }>;

/** Độ dài mô tả SEO tối đa (Google cắt khoảng 155-160 ký tự). */
const META_DESCRIPTION_MAX = 160;

/**
 * Dùng chung cho generateMetadata và page trong cùng một request (React.cache) để không gọi API hai lần.
 * Mô tả được sanitize ngay ở server trước khi đưa xuống client component.
 */
const fetchProduct = cache(async (slug: string) => {
  try {
    const product = await getProductDetail(slug);
    return product && { ...product, description: sanitizeRichText(product.description ?? '') };
  } catch {
    return null;
  }
});

const plainText = (html: string) =>
  html
    .replace(/<[^>]*>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, META_DESCRIPTION_MAX);

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { slug } = await params;
  const product = await fetchProduct(slug);
  if (!product) notFound();
  const description = plainText(product.description) || product.name;

  return {
    title: product.name,
    description,
    openGraph: {
      title: product.name,
      description,
      images: product.thumbnail ? [{ url: product.thumbnail }] : [],
    },
    alternates: {
      canonical: process.env.NEXT_PUBLIC_API_URL + '/product/' + slug,
    },
  };
}

export default async function ProductDetailPage({
  params,
  searchParams,
}: {
  params: Params;
  searchParams: SearchParams;
}) {
  const { slug, locale } = await params;
  setRequestLocale(locale);
  const { pvid } = await searchParams;
  const t = await getTranslations();

  const product = await fetchProduct(slug);
  if (!product) notFound();
  // Slug của một biến thể: chuyển về trang sản phẩm cha và chọn sẵn biến thể đó.
  if (product.parentId) {
    const parent = await getProductById(product.parentId).catch(() => null);
    if (!parent) notFound();
    redirect({ href: { pathname: `/product/${parent.slug}`, query: { pvid: product.id } }, locale });
  }

  const crumb: Breadcrumb[] = [
    { pageName: t('common.home'), url: '/home' },
    { pageName: t('common.product'), url: '/product' },
    { pageName: product.name, url: '' },
  ];

  return (
    <React.Fragment>
      <BreadcrumbComponent items={crumb} />
      <ProductDetail product={product} pvid={typeof pvid === 'string' ? pvid : null} />
      <section className="w-full max-w-6xl mx-auto px-4 my-3">
        <div className="bg-white p-5">
          <h2 className="bg-yellow-100 px-2 py-1 rounded-sm uppercase">{t('product.product_review')}</h2>
          <ReviewProduct productId={product.id} reviewStatictis={toReviewStatistic(product.ratingSummary)} />
        </div>
      </section>
      <RelatedProduct productId={product.id} />
    </React.Fragment>
  );
}
