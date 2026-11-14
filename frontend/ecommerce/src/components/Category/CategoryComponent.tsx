'use client';
import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getAllCategoryJson } from '@/api/categoryApi';
import { Link } from '@/i18n/routing';
import type { Category } from '@/models/types';
import { CATEGORY_KEY } from '@/utils/constants/queryKey';
import CategoryComponentSkeleton from './CategoryComponentSkeleton';

type Tree = Extract<Category, { children: unknown }>;

/** Menu danh mục (mega menu): cột trái danh mục gốc, bên phải danh mục con của mục đang trỏ/focus. */
export default function CategoryComponent() {
  const { data: categoryData, isLoading } = useQuery({
    queryKey: [CATEGORY_KEY],
    queryFn: async () => (await getAllCategoryJson()).data as Tree[],
  });
  const [selected, setSelected] = useState<Tree | null>(null);

  if (isLoading) return <CategoryComponentSkeleton />;
  const current = selected ?? categoryData?.[0] ?? null;

  return (
    <div className="flex flex-col sm:flex-row max-h-[70vh] sm:h-[30rem]">
      <ul className="list-none sm:w-1/4 overflow-y-auto border-b-2 sm:border-b-0 sm:border-r-2 border-gray-300">
        {categoryData?.map((category) => (
          <li key={category.id}>
            <Link
              href={`/category/${category.slug}`}
              onMouseEnter={() => setSelected(category)}
              onFocus={() => setSelected(category)}
              className={`block p-3 hover:bg-gray-100 ${current?.id === category.id ? 'bg-gray-100' : ''}`}
            >
              {category.name}
            </Link>
          </li>
        ))}
      </ul>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 p-3 sm:w-3/4 overflow-y-auto content-start">
        {current?.children.map((child) => (
          <div key={child.id} className="flex flex-col">
            <Link href={`/category/${current.slug}/${child.slug}`} className="font-semibold w-fit hover:underline">
              {child.name}
            </Link>
            {child.children?.map((item) => (
              <Link key={item.id} href={`/category/${current.slug}/${child.slug}/${item.slug}`} className="hover:underline w-fit">
                {item.name}
              </Link>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
