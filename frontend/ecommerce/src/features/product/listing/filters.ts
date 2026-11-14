import { ProductFilter } from '@/models/types';

export const PRODUCT_PAGE_SIZE = 20;

type Params = URLSearchParams | Record<string, string | string[] | undefined>;

const read = (params: Params, key: string) => {
  const v = params instanceof URLSearchParams ? params.get(key) : params[key];
  return (Array.isArray(v) ? v[0] : v) || undefined;
};

const num = (v?: string) => (v !== undefined && v !== '' && Number.isFinite(Number(v)) ? Number(v) : undefined);

/** URL là nguồn sự thật cho bộ lọc: đọc được cả ở server (searchParams) lẫn client (useSearchParams). */
export function parseProductFilter(params: Params): ProductFilter {
  const category = read(params, 'category');
  return {
    keyword: read(params, 'keyword'),
    category: category ? [category] : undefined,
    rating: num(read(params, 'rating')),
    startPrice: num(read(params, 'startPrice')),
    endPrice: num(read(params, 'endPrice')),
  };
}

export function filterToQuery(filter: ProductFilter): Record<string, string> {
  const q: Record<string, string> = {};
  if (filter.keyword) q.keyword = filter.keyword;
  if (filter.category?.[0]) q.category = filter.category[0];
  if (filter.rating) q.rating = String(filter.rating);
  if (filter.startPrice !== undefined) q.startPrice = String(filter.startPrice);
  if (filter.endPrice !== undefined) q.endPrice = String(filter.endPrice);
  return q;
}

/** Key ổn định (không phụ thuộc thứ tự thuộc tính) cho React Query. */
export const productListKey = (filter: ProductFilter) => ['product', 'list', filterToQuery(filter)] as const;
