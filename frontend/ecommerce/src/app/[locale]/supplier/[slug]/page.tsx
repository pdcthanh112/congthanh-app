import React from 'react';
import { getSupplierBySlug } from '@/api/supplierApi';
import SupplierInfo from '@/components/Supplier/SupplierInfo';

async function getSupplierInfo(slug: string) {
  return await getSupplierBySlug(slug);
}

export default async function SupplierPage({ params }: { params: { slug: string; locale: string; path: string[] } }) {
  const { slug, locale, path } = params;

  const supplier = await getSupplierInfo(slug).then((response) => response.data);

  return (
    <React.Fragment>
      <SupplierInfo supplier={supplier} />
    </React.Fragment>
  );
}
