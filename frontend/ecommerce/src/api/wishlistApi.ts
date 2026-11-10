import { gql } from '@/graphql/client';
import { AddToWishlistMutation, MyWishlistQuery, RemoveFromWishlistMutation } from '@/graphql/documents';
import { ok, toProduct } from '@/graphql/legacy';
import { Response, Wishlist } from '@/models/types';

// Wishlist (customer-service, qua supergraph) của khách đang đăng nhập; customerId giữ cho tương thích.
export const getWishlistByCustomer = async (customerId: string): Promise<Response<Wishlist>> => {
  const { me } = await gql(MyWishlistQuery, { page: 1, size: 100 });
  return ok({ id: me.id, custommer: customerId, product: me.wishlist.items.map((i) => toProduct(i.product)) });
};

export const addProductToWishlist = async (_customerId: string, productId: string) =>
  ok((await gql(AddToWishlistMutation, { productId })).addToWishlist);

export const removeProductFromWishlist = async (_customerId: string, productId: string) =>
  ok((await gql(RemoveFromWishlistMutation, { productId })).removeFromWishlist);
