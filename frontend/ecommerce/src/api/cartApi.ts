import { gql } from '@/graphql/client';
import {
  AddToCartMutation,
  ClearCartMutation,
  MyCartQuery,
  RemoveCartItemMutation,
  UpdateCartItemMutation,
} from '@/graphql/documents';
import type { Cart } from '@/graphql/types';

// Giỏ hàng (cart-service, qua supergraph): mỗi khách một giỏ, xác định theo token.
export const getMyCart = async (): Promise<Cart> => (await gql(MyCartQuery)).myCart;

export const addProductToCart = async ({ productId, quantity }: { productId: string; quantity: number }) =>
  (await gql(AddToCartMutation, { productId, quantity })).addToCart;

export const updateCartItem = async ({ itemId, quantity }: { itemId: string; quantity: number }) =>
  (await gql(UpdateCartItemMutation, { itemId, quantity })).updateCartItem;

export const deleteCartItem = async (itemId: string) => (await gql(RemoveCartItemMutation, { itemId })).removeCartItem;

export const clearCart = async () => (await gql(ClearCartMutation)).clearCart;
