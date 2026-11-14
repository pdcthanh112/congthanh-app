import { gqlWith } from '@/graphql/client';
import { CategoryQuery, ProductQuery, ProductsQuery, SearchProductsQuery } from '@/graphql/documents';
import { ok, paged, toProduct } from '@/graphql/legacy';
import {
  PaginationParams,
  Product,
  ProductFilter,
  ProductOptionValueDisplay,
  ProductOptionValueGet,
  ProductVariant,
  ProductVariantAttribute,
  Response,
  ResponseWithPagination,
} from '@/models/types';

/** Dữ liệu catalog là public: khi gọi từ server thì không gắn token và cache 60 giây (ISR). */
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

export const getProductBySlug = async (productSlug: string): Promise<Response<Product>> => {
  const { product } = await gqlWith(CATALOG, ProductQuery, { slug: productSlug });
  if (!product) throw new Error('Product not found');
  return ok(toProduct(product));
};

export const getProductDetailBySlug = getProductBySlug;

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

// Biến thể/tuỳ chọn sản phẩm chưa có trong supergraph: trả rỗng để trang chi tiết hiển thị sản phẩm đơn.
export const getVariantAttributeValueByProduct = async (
  _productId: string
): Promise<Response<ProductVariantAttribute[]>> => ok([]);
export async function getProductOptionValues(_productId: string): Promise<ProductOptionValueGet[]> {
  return [];
}
export async function getProductVariationsByParentId(_parentId: string): Promise<ProductVariant[]> {
  return [];
}
export async function getProductOptionValueByProductId(_productId: string): Promise<ProductOptionValueDisplay[]> {
  return [];
}
