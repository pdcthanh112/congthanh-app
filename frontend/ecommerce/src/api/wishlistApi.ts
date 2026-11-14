import { gql } from '@/graphql/client';
import { AddToWishlistMutation, MyWishlistQuery, RemoveFromWishlistMutation } from '@/graphql/documents';
import { ok, toProduct } from '@/graphql/legacy';
import { Product, Response } from '@/models/types';

// Wishlist (customer-service, qua supergraph) của khách đang đăng nhập, xác định theo token.
export const getMyWishlist = async (): Promise<Response<{ id: string; product: Product[] }>> => {
  const { me } = await gql(MyWishlistQuery, { page: 1, size: 100 });
  return ok({ id: me.id, product: me.wishlist.items.map((i) => toProduct(i.product)) });
};

export const addProductToWishlist = async (productId: string) =>
  ok((await gql(AddToWishlistMutation, { productId })).addToWishlist);

export const removeProductFromWishlist = async (productId: string) =>
  ok((await gql(RemoveFromWishlistMutation, { productId })).removeFromWishlist);
