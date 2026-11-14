import { cache } from 'react';
import { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { setRequestLocale } from 'next-intl/server';
import { getSupplierBySlug } from '@/api/supplierApi';
import SupplierInfo from '@/components/Supplier/SupplierInfo';
import type { Locale } from '@/lib/locales';

type Props = { params: Promise<{ slug: string; locale: Locale }> };

const fetchSupplier = cache(async (slug: string) => {
  try {
    return (await getSupplierBySlug(slug)).data;
  } catch {
    return null;
  }
});

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const supplier = await fetchSupplier((await params).slug);
  return supplier ? { title: supplier.name } : {};
}

export default async function SupplierPage({ params }: Props) {
  const { slug, locale } = await params;
  setRequestLocale(locale);
  const supplier = await fetchSupplier(slug);
  if (!supplier) notFound();
  return <SupplierInfo supplier={supplier} />;
}
