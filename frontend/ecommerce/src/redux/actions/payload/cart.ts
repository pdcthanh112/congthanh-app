import { Cart, CartItem } from '@/models/types/CartModel';

export type FetchCartPayload = {
  params: { customerId: string };
};

export type FetchCartStartPayload = {
  params: { customerId: string };
};

export type FetchCartSucceededPayload = {
  data: Cart[];
};

export type FetchCartFailedPayload = {
  error: string;
};
export type FetchCartClearPayload = {
  data: [];
};
export type CreateNewCartRequestedPayload = {
  values: { id: string; name: string };
};

export type CreateNewCartStartPayload = {
  name: string;
  customer: string;
  isDefault: boolean;
};

export type CreateNewCartSucceededPayload = {
  data: Cart;
};

export type CreateNewCartFailedPayload = {
  error: string;
};

export type DeleteCartRequestedPayload = {
  values: { id: string };
};

export type DeleteCartStartPayload = {
  cartId: number;
};

export type DeleteCartSucceededPayload = {
  cartId: number
};

export type DeleteCartFailedPayload = {
  error: string;
};

export type AddItemToCartRequestedPayload = {
  values: { productId: string; quantity: number; cartId: string };
};

export type AddItemToCartStartPayload = {
  product: string;
  quantity: number;
  cartId: string;
};

export type AddItemToCartSucceededPayload = {
  item: CartItem;
};

export type AddItemToCartFailedPayload = {
  error: string;
};

export type RemoveItemFromCartRequestedPayload = {
  values: { productId: string; cartId: string };
};

export type RemoveItemFromCartStartPayload = {
  values: { productId: string; cartId: string };
};

export type RemoveItemFromCartSucceededPayload = {
  token: string;
};

export type RemoveItemFromCartFailedPayload = {
  error: string;
};

export type updateItemQuantityRequestPayload = {
  values: [];
};

export type updateItemQuantityStartPayload = {
  values: { cartId: number; itemId: number; quantity: number };
};

export type updateItemQuantitySucceededPayload = {
  data: [];
};

export type updateItemQuantityFailedPayload = {
  error: string;
};

export type updateItemQuantityClearPayload = {
  data: [];
};
