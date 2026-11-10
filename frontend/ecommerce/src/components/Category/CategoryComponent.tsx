'use client';
import React, { useState } from 'react';
import { CATEGORY_KEY } from '@/utils/constants/queryKey';
import { useQuery } from '@tanstack/react-query';
import { getAllCategoryJson } from '@/api/categoryApi';
import Link from 'next/link';
import { Category } from '@/models/types';
import CategoryComponentSkeleton from './CategoryComponentSkeleton';

export default function CategoryComponent() {
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);

  const { data: categoryData, isLoading } = useQuery({
    queryKey: [CATEGORY_KEY],
    queryFn: async () => await getAllCategoryJson().then((response) => response.data),
  });

  if (isLoading) return <CategoryComponentSkeleton />;

  return (
    <div className="flex flex-row h-[30rem]">
      <div className="flex w-1/4">
        <ul className="list-none w-full h-full border-r-2 border-gray-300">
          {categoryData?.map((category: Category) => (
            <li key={category.id} onMouseEnter={() => setSelectedCategory(category)} className="hover:bg-gray-100 p-3">
              <Link href={`/category/${category.slug}`}>{category.name}</Link>
            </li>
          ))}
        </ul>
      </div>

      <div className="grid grid-cols-4 gap-3 p-3 w-3/4">
        {selectedCategory && 'children' in selectedCategory &&
          selectedCategory.children.map((child: Category) => (
            <div key={child.id} className="flex flex-col">
              <Link href={`/category/cha/${child.slug}`} className="font-semibold w-fit hover:underline">
                {child.name}
              </Link>

              {'children' in child && child.children &&
                child.children.map((item: Category) => (
                  <Link href={`/category/${child.slug}/${item.slug}`} className="hover:underline w-fit">
                    {item.name}
                  </Link>
                ))}
            </div>
          ))}
      </div>
    </div>
  );
}
