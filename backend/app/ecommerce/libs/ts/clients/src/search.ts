import { RestClient } from '@ecommerce/platform';
import { z } from 'zod';

const Bucket = z.object({ value: z.string(), count: z.number() });
const FacetGroup = z.object({ key: z.string(), values: z.array(Bucket) });
const Hit = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string().nullish(),
  price: z.number(),
  priceMax: z.number().nullish(),
  currency: z.string().nullish(),
  score: z.number().nullish(),
});
const Result = z.object({
  hits: z.array(Hit),
  total: z.number(),
  categories: z.array(z.object({ categoryId: z.string(), count: z.number() })),
  brands: z.array(Bucket).nullish(),
  attributes: z.array(FacetGroup).nullish(),
  options: z.array(FacetGroup).nullish(),
});
export type SearchHit = z.infer<typeof Hit>;
export type SearchResult = z.infer<typeof Result>;
export type SearchFacetGroup = z.infer<typeof FacetGroup>;

export type SearchSort = 'relevance' | 'price_asc' | 'price_desc' | 'newest';

/** Truy vấn tìm kiếm sản phẩm. Nhiều giá trị của một bộ lọc là OR, các bộ lọc khác nhau là AND (ADR 0030). */
export interface SearchQuery {
  text?: string;
  categoryId?: string;
  brandIds?: readonly string[];
  /** code thuộc tính -> giá trị */
  attributes?: Readonly<Record<string, readonly string[]>>;
  /** tên trục variant -> giá trị */
  options?: Readonly<Record<string, readonly string[]>>;
  /** Bắt buộc khi lọc theo giá (giá khác currency không so được). */
  currency?: string;
  minPrice?: number;
  maxPrice?: number;
  sort?: SearchSort;
  /** Từ 0. */
  page?: number;
  size?: number;
}

/** search-service (Go, Elasticsearch) qua REST: tìm theo từ khoá, lọc thuộc tính/option/thương hiệu, facet. */
export class SearchClient {
  private readonly rest: RestClient;

  constructor(baseUrl: string) {
    this.rest = new RestClient({ baseUrl, name: 'search-service', timeoutMs: 2_000 });
  }

  search(q: SearchQuery): Promise<SearchResult> {
    return this.rest.data('/api/ecommerce/search/products', Result, { query: toParams(q) });
  }
}

/** Đổi truy vấn sang tham số URL của search-service; nhiều giá trị nối bằng dấu phẩy. */
export function toParams(q: SearchQuery): Record<string, string | number> {
  const p: Record<string, string | number> = {};
  if (q.text) p.q = q.text;
  if (q.categoryId) p.categoryId = q.categoryId;
  if (q.brandIds?.length) p.brand = q.brandIds.join(',');
  for (const [code, values] of Object.entries(q.attributes ?? {}))
    if (values.length) p[`attr.${code}`] = values.join(',');
  for (const [name, values] of Object.entries(q.options ?? {})) if (values.length) p[`opt.${name}`] = values.join(',');
  if (q.currency) p.currency = q.currency;
  if (q.minPrice !== undefined) p.minPrice = q.minPrice;
  if (q.maxPrice !== undefined) p.maxPrice = q.maxPrice;
  if (q.sort) p.sort = q.sort;
  if (q.page !== undefined) p.page = q.page;
  if (q.size !== undefined) p.size = q.size;
  return p;
}
