import { gql } from '@/graphql/client';
import {
  AddToCartMutation,
  ClearCartMutation,
  MyCartQuery,
  RemoveCartItemMutation,
  UpdateCartItemMutation,
} from '@/graphql/documents';
import { ok, toCart, toCartItemDetail } from '@/graphql/legacy';
import { CreateCartForm } from '@/models/form';
import { Cart, CartItemDetail, Response } from '@/models/types';

// Giỏ hàng (cart-service, qua supergraph): mỗi khách một giỏ, lấy theo token, không cần customerId/cartId.
type Id = string | number;

export const getMyCart = async (): Promise<Cart> => toCart((await gql(MyCartQuery)).myCart);

/** Giữ tên cũ: trả về giỏ duy nhất của khách đang đăng nhập. */
export const getCartByCustomerId = async (customerId: string): Promise<Response<Cart[]>> =>
  ok([toCart((await gql(MyCartQuery)).myCart, customerId)]);

export const getCartById = async (_id: Id): Promise<Response<Cart>> => ok(await getMyCart());

/** Không còn nhiều giỏ: trả về giỏ hiện tại. */
export const createNewCart = async (_data: CreateCartForm): Promise<Response<Cart>> => ok(await getMyCart());

export const deleteCart = async (_cartId: Id): Promise<Response<Cart>> =>
  ok(toCart((await gql(ClearCartMutation)).clearCart));

export const addProductToCart = async ({ productId, quantity }: { productId: string; quantity: number; cartId?: Id }) =>
  ok(toCart((await gql(AddToCartMutation, { productId, quantity })).addToCart));

export const updateCartItem = async ({ cartItemId, quantity }: { cartItemId: Id; quantity: number }) =>
  ok(toCart((await gql(UpdateCartItemMutation, { itemId: String(cartItemId), quantity })).updateCartItem));

export const deleteCartItem = async (itemId: Id) =>
  ok(toCart((await gql(RemoveCartItemMutation, { itemId: String(itemId) })).removeCartItem));

export const getItemDetail = async ({
  itemId,
}: {
  cartId?: Id;
  itemId: Id;
}): Promise<Response<CartItemDetail | null>> => {
  const line = (await gql(MyCartQuery)).myCart.lines.find((l) => l.id === String(itemId));
  return ok(line ? toCartItemDetail(line) : null);
};
