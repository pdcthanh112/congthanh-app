import React from 'react';
import { useSearchParams } from 'next/navigation';

export default async function Page({params}: any) {

    const { subcategory } = params;
  return <div>aaaaaaaaaaaaaaaaaaa{subcategory}</div>;
}
