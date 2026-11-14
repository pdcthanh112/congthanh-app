'use client';

import React, { useEffect, useState } from 'react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { getAllProduct } from '@/api/productApi';
import { getAllCategoryJson } from '@/api/categoryApi';
import StarRating from '@/components/common/StarRating';
import { Button, Checkbox, Input, Label } from '@/components/ui';
import ShowListProduct from '@/features/product/component/ShowListProduct';
import useDebounce from '@/hooks/useDebounce';
import { usePathname, useRouter } from '@/i18n/routing';
import { cn } from '@/lib/utils';
import { ProductFilter } from '@/models/types';
import { CATEGORY_KEY } from '@/utils/constants/queryKey';
import { filterToQuery, parseProductFilter, PRODUCT_PAGE_SIZE, productListKey } from './filters';

const CATEGORY_PREVIEW = 10;

export default function ProductListing() {
  const t = useTranslations('product');
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const filter = parseProductFilter(searchParams);

  const setFilter = (next: ProductFilter) =>
    router.replace({ pathname, query: filterToQuery(next) }, { scroll: false });

  // Ô nhập là state cục bộ (controlled), chỉ đẩy lên URL sau khi ngừng gõ.
  const [keyword, setKeyword] = useState(filter.keyword ?? '');
  const [price, setPrice] = useState({
    start: filter.startPrice?.toString() ?? '',
    end: filter.endPrice?.toString() ?? '',
  });
  const debouncedKeyword = useDebounce(keyword, 500);
  const debouncedPrice = useDebounce(price, 500);

  useEffect(() => {
    const next = debouncedKeyword.trim() || undefined;
    if (next !== filter.keyword) setFilter({ ...filter, keyword: next });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  useEffect(() => {
    const toNum = (v: string) => (v === '' ? undefined : Number(v));
    const startPrice = toNum(debouncedPrice.start);
    const endPrice = toNum(debouncedPrice.end);
    if (startPrice !== filter.startPrice || endPrice !== filter.endPrice) setFilter({ ...filter, startPrice, endPrice });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedPrice]);

  const [showAllCategory, setShowAllCategory] = useState(false);
  const { data: categories = [] } = useQuery({
    queryKey: [CATEGORY_KEY, 'tree'],
    queryFn: async () => (await getAllCategoryJson()).data,
  });
  const visibleCategories = showAllCategory ? categories : categories.slice(0, CATEGORY_PREVIEW);

  const { data, isPending, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: productListKey(filter),
    queryFn: async ({ pageParam }) => (await getAllProduct({ page: pageParam, limit: PRODUCT_PAGE_SIZE }, filter)).data,
    initialPageParam: 1,
    getNextPageParam: (last) =>
      last.paginationInfo.page < last.paginationInfo.totalPage ? last.paginationInfo.page + 1 : undefined,
  });
  const products = data?.pages.flatMap((p) => p.responseList) ?? [];

  const toggleCategory = (slug: string) =>
    setFilter({ ...filter, category: filter.category?.[0] === slug ? undefined : [slug] });

  // Bấm lại đúng mức sao đang chọn thì bỏ lọc, bấm mức khác thì chuyển sang mức đó.
  const toggleRating = (star: number) => setFilter({ ...filter, rating: filter.rating === star ? undefined : star });

  const clearFilter = () => {
    setKeyword('');
    setPrice({ start: '', end: '' });
    setFilter({});
  };

  return (
    <div className="mx-auto mt-5 grid w-full max-w-7xl grid-cols-1 gap-6 px-4 lg:grid-cols-12">
      <aside className="space-y-6 lg:col-span-3" aria-label={t('filter')}>
        <div>
          <Label htmlFor="product-keyword" className="mb-2 block font-semibold">
            {t('search')}
          </Label>
          <Input
            id="product-keyword"
            value={keyword}
            placeholder={t('search_placeholder')}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>

        {categories.length > 0 && (
          <fieldset>
            <legend className="mb-2 font-semibold">{t('category')}</legend>
            <ul className="space-y-1">
              {visibleCategories.map((item) => (
                <li key={item.id} className="flex items-center gap-2">
                  <Checkbox
                    id={`cat-${item.id}`}
                    checked={filter.category?.[0] === item.slug}
                    onCheckedChange={() => toggleCategory(item.slug)}
                  />
                  <Label htmlFor={`cat-${item.id}`} className="font-normal">
                    {item.name}
                  </Label>
                </li>
              ))}
            </ul>
            {categories.length > CATEGORY_PREVIEW && (
              <Button variant="link" size="sm" className="px-0" onClick={() => setShowAllCategory((v) => !v)}>
                {showAllCategory ? t('show_less') : t('show_more')}
              </Button>
            )}
          </fieldset>
        )}

        <fieldset>
          <legend className="mb-2 font-semibold">{t('rating_filter')}</legend>
          <div className="space-y-1">
            {[5, 4, 3, 2, 1].map((star) => (
              <button
                key={star}
                type="button"
                aria-pressed={filter.rating === star}
                onClick={() => toggleRating(star)}
                className={cn(
                  'flex w-full items-center gap-2 rounded px-2 py-1 text-left hover:bg-muted',
                  filter.rating === star && 'bg-muted'
                )}
              >
                <StarRating value={star} size={14} />
                <span>{t('star_up', { count: star })}</span>
              </button>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="mb-2 font-semibold">{t('price')}</legend>
          <div className="flex gap-2">
            <Input
              type="number"
              min={0}
              inputMode="numeric"
              aria-label={t('price_from')}
              placeholder={t('price_from')}
              value={price.start}
              onChange={(e) => setPrice((p) => ({ ...p, start: e.target.value }))}
            />
            <Input
              type="number"
              min={0}
              inputMode="numeric"
              aria-label={t('price_to')}
              placeholder={t('price_to')}
              value={price.end}
              onChange={(e) => setPrice((p) => ({ ...p, end: e.target.value }))}
            />
          </div>
        </fieldset>

        <Button type="button" variant="outline" className="w-full" onClick={clearFilter}>
          {t('clear_filter')}
        </Button>
      </aside>

      <section className="lg:col-span-9">
        <ShowListProduct listProduct={products} loading={isPending} />
        {isFetchingNextPage && <ShowListProduct loading skeletonCount={5} />}
        {hasNextPage && (
          <Button
            type="button"
            onClick={() => fetchNextPage()}
            className="mt-5 w-full"
            disabled={isFetchingNextPage}
          >
            {isFetchingNextPage ? t('loading_more') : t('load_more')}
          </Button>
        )}
      </section>
    </div>
  );
}
