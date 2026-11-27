import { gql, gqlWith } from '@/graphql/client';
import {
  CategoryQuery,
  ProductDetailQuery,
  ProductQuery,
  ProductRecommendationsQuery,
  ProductsQuery,
  SearchProductsQuery,
  TrackProductViewMutation,
} from '@/graphql/documents';
import { paged, toProduct } from '@/graphql/legacy';
import type { ProductDetail, Recommendation, RecommendationKind } from '@/graphql/types';
import { PaginationParams, Product, ProductFilter, ResponseWithPagination } from '@/models/types';

const CATALOG = { anonymous: true, revalidate: 60 } as const;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Nhận id hoặc slug danh mục, trả id (supergraph lọc sản phẩm theo id). */
const resolveCategoryId = async (idOrSlug?: string | null) => {
  if (!idOrSlug) return null;
  if (UUID.test(idOrSlug)) return idOrSlug;
  return (await gqlWith(CATALOG, CategoryQuery, { slug: idOrSlug })).category?.id ?? idOrSlug;
};

/**
 * Supergraph chỉ hỗ trợ lọc theo danh mục (products) và tìm theo từ khoá (searchProducts, không phân trang).
 * Đánh giá và khoảng giá được lọc trên trang kết quả trả về cho tới khi API hỗ trợ lọc phía server.
 */
const matchesClientFilter = (p: Product, filter: ProductFilter) => {
  if (filter.rating && (p.ratingSummary?.average ?? 0) < filter.rating) return false;
  const price = p.price?.amount;
  if (price !== undefined) {
    if (filter.startPrice !== undefined && price < filter.startPrice) return false;
    if (filter.endPrice !== undefined && price > filter.endPrice) return false;
  }
  return true;
};

// Sản phẩm lấy qua supergraph storefront (storefront-bff ghép giá, tồn kho, đánh giá, wishlist).
export const getAllProduct = async (
  pagination: PaginationParams,
  filter: ProductFilter = {}
): Promise<ResponseWithPagination<Product>> => {
  if (filter.keyword?.trim()) {
    const { searchProducts } = await gqlWith(CATALOG, SearchProductsQuery, { keyword: filter.keyword.trim() });
    const items = searchProducts.map(toProduct).filter((p) => matchesClientFilter(p, filter));
    return paged(items, 1, Math.max(items.length, 1), items.length);
  }
  const { products } = await gqlWith(CATALOG, ProductsQuery, {
    categoryId: await resolveCategoryId(filter.category?.[0]),
    page: pagination.page,
    limit: pagination.limit,
  });
  return paged(
    products.items.map(toProduct).filter((p) => matchesClientFilter(p, filter)),
    products.page,
    products.limit,
    products.totalItems
  );
};

export const getProductById = async (productId: string): Promise<Product> => {
  const { product } = await gqlWith(CATALOG, ProductQuery, { id: productId });
  if (!product) throw new Error('Product not found');
  return toProduct(product);
};

/** page tính từ 0 như API cũ. */
export const getProductByCategory = async (categoryIdOrSlug: string, page: number, limit: number) => {
  const categoryId = await resolveCategoryId(categoryIdOrSlug);
  const { products } = await gqlWith(CATALOG, ProductsQuery, { categoryId, page: page + 1, limit });
  return paged(products.items.map(toProduct), products.page, products.limit, products.totalItems);
};

export const searchProducts = async (keyword: string): Promise<Product[]> => {
  const { searchProducts } = await gqlWith(CATALOG, SearchProductsQuery, { keyword });
  return searchProducts.map(toProduct);
};

/** Trang chi tiết (kèm biến thể, thông số); null khi không có hoặc đã ngừng bán. */
export const getProductDetail = async (slug: string): Promise<ProductDetail | null> =>
  (await gqlWith(CATALOG, ProductDetailQuery, { slug })).product;

export const getProductRecommendations = async (
  productId: string,
  kind: RecommendationKind,
  limit: number
): Promise<Recommendation[]> =>
  (await gql(ProductRecommendationsQuery, { productId, kind, limit })).product?.recommendations ?? [];

/** Tín hiệu "đã xem" cho recommendation-service; khách chưa đăng nhập không có định danh nên BFF bỏ qua. */
export const trackProductView = async (productId: string): Promise<boolean> =>
  (await gql(TrackProductViewMutation, { productId })).trackProductView;
