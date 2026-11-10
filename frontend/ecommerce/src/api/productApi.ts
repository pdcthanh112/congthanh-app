import { gql } from '@/graphql/client';
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

// Sản phẩm lấy qua supergraph storefront (storefront-bff ghép giá, tồn kho, đánh giá, wishlist).
export const getAllProduct = async (
  pagination: PaginationParams,
  filter: ProductFilter = {}
): Promise<ResponseWithPagination<Product>> => {
  const { products } = await gql(ProductsQuery, {
    categoryId: filter.category?.[0] ?? null,
    page: pagination.page,
    limit: pagination.limit,
  });
  return paged(products.items.map(toProduct), products.page, products.limit, products.totalItems);
};

export const getProductById = async (productId: string): Promise<Product> => {
  const { product } = await gql(ProductQuery, { id: productId });
  if (!product) throw new Error('Product not found');
  return toProduct(product);
};

export const getProductBySlug = async (productSlug: string): Promise<Response<Product>> => {
  const { product } = await gql(ProductQuery, { slug: productSlug });
  if (!product) throw new Error('Product not found');
  return ok(toProduct(product));
};

export const getProductDetailBySlug = getProductBySlug;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Nhận id hoặc slug danh mục; page tính từ 0 như API cũ. */
export const getProductByCategory = async (categoryIdOrSlug: string, page: number, limit: number) => {
  const categoryId = UUID.test(categoryIdOrSlug)
    ? categoryIdOrSlug
    : ((await gql(CategoryQuery, { slug: categoryIdOrSlug })).category?.id ?? categoryIdOrSlug);
  const { products } = await gql(ProductsQuery, { categoryId, page: page + 1, limit });
  return paged(products.items.map(toProduct), products.page, products.limit, products.totalItems);
};

export const searchProducts = async (keyword: string): Promise<Product[]> => {
  const { searchProducts } = await gql(SearchProductsQuery, { keyword });
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

/** Ảnh đại diện: GraphQL trả sẵn thumbnail trong Product; giữ hàm cho component cũ. */
export const getDefaultImageByProductId = async (
  productId: string
): Promise<Response<{ id: number; product: string; imagePath: string }>> => {
  const { product } = await gql(ProductQuery, { id: productId });
  return ok({ id: 0, product: productId, imagePath: product?.thumbnail ?? '' });
};

/** Số lượng đã bán chưa có trong supergraph. */
export const getSoldByProduct = async (_productId: string): Promise<Response<number>> => ok(0);
