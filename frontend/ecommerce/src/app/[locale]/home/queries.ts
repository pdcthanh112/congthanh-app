import { getAllProduct } from '@/api/productApi';
import { PRODUCT_KEY } from '@/utils/constants/queryKey';

export const HOME_PAGE_SIZE = 10;

/** Dùng chung cho prefetch ở server và useQuery ở client để key/hàm luôn khớp nhau. */
export const homeProductsQuery = (page: number) => ({
  queryKey: [PRODUCT_KEY, 'home', page],
  queryFn: async () => (await getAllProduct({ page, limit: HOME_PAGE_SIZE })).data,
});
