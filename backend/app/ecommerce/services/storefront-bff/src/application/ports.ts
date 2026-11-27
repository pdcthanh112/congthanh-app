import type {
  CartClient,
  CheckoutClient,
  CustomerClient,
  InventoryClient,
  OrderClient,
  ProductClient,
  PromotionClient,
  RecommendationClient,
  SearchClient,
} from '@ecommerce/clients';

/**
 * Cổng ra tới các service phía sau. Adapter là các client trong @ecommerce/clients (gRPC/REST); test
 * thay bằng bản giả. Chỉ khai báo phần BFF dùng để test không phải giả cả client.
 */
export type ProductPort = Pick<ProductClient, 'byIds' | 'detail' | 'bySlug' | 'list' | 'byCategory' | 'variants' | 'optionValues'>;
/** Tìm kiếm, lọc, facet cho storefront đi qua search-service (ADR 0030), không qua product-service. */
export type SearchPort = Pick<SearchClient, 'search'>;
export type InventoryPort = Pick<InventoryClient, 'levels'>;
export type CartPort = Pick<CartClient, 'defaultCart' | 'ensureDefault' | 'addItem' | 'updateItem' | 'removeItem' | 'clear'>;
export type PromotionPort = Pick<PromotionClient, 'quote'>;
export type OrderPort = Pick<OrderClient, 'get' | 'history' | 'progress' | 'cancel'>;
export type CheckoutPort = Pick<CheckoutClient, 'open' | 'place' | 'get'>;
export type RecommendationPort = Pick<RecommendationClient, 'forProduct' | 'trending' | 'forYou' | 'track'>;
export type AddressPort = Pick<CustomerClient, 'shippingAddress'>;

export const PRODUCTS = Symbol('PRODUCTS');
export const INVENTORY = Symbol('INVENTORY');
export const CARTS = Symbol('CARTS');
export const PROMOTIONS = Symbol('PROMOTIONS');
export const ORDERS = Symbol('ORDERS');
export const ADDRESSES = Symbol('ADDRESSES');
export const CHECKOUTS = Symbol('CHECKOUTS');
export const RECOMMENDATIONS = Symbol('RECOMMENDATIONS');
export const SEARCH = Symbol('SEARCH');
