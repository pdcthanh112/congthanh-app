import type { CartClient, InventoryClient, OrderClient, ProductClient, PromotionClient } from '@ecom/clients';

/** Cổng ra tới service phía sau (adapter: client trong @ecom/clients). */
export type ProductPort = Pick<ProductClient, 'byIds' | 'detail' | 'bySlug' | 'list' | 'byCategory' | 'search'>;
export type InventoryPort = Pick<InventoryClient, 'levels' | 'adjust'>;
export type CartPort = Pick<CartClient, 'defaultCart'>;
export type PromotionPort = Pick<PromotionClient, 'byCode'>;
export type OrderPort = Pick<OrderClient, 'get' | 'cancel'>;

export const PRODUCTS = Symbol('PRODUCTS');
export const INVENTORY = Symbol('INVENTORY');
export const CARTS = Symbol('CARTS');
export const PROMOTIONS = Symbol('PROMOTIONS');
export const ORDERS = Symbol('ORDERS');
