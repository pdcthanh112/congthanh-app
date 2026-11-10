export interface PaginationParams {
  page: number;
  limit: number;
  sort?: string;
  order?: 'asc' | 'desc';
  totalPage?: number;
}

export interface ProductListQuery extends PaginationParams {
  sort?: 'createdAt' | 'view' | 'sold' | 'price'
  // exclude?: string
  rating_filter?: number | string
  price_max?: number | string
  price_min?: number | string
  name?: string
  category?: string
}
