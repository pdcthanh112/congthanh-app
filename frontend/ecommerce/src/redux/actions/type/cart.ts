import { Cart } from '@/models/types/CartModel';
import * as actionName from '../name/cart';
import {
  AddItemToCartFailedPayload,
  AddItemToCartRequestedPayload,
  AddItemToCartStartPayload,
  AddItemToCartSucceededPayload,
  CreateNewCartFailedPayload,
  CreateNewCartRequestedPayload,
  CreateNewCartStartPayload,
  CreateNewCartSucceededPayload,
  DeleteCartFailedPayload,
  DeleteCartRequestedPayload,
  DeleteCartStartPayload,
  DeleteCartSucceededPayload,
  FetchCartClearPayload,
  FetchCartFailedPayload,
  FetchCartRequestedPayload,
  FetchCartStartPayload,
  FetchCartSucceededPayload,
  RemoveItemFromCartFailedPayload,
  RemoveItemFromCartRequestedPayload,
  RemoveItemFromCartStartPayload,
  RemoveItemFromCartSucceededPayload,
  updateItemQuantityClearPayload,
  updateItemQuantityFailedPayload,
  updateItemQuantityRequestPayload,
  updateItemQuantityStartPayload,
  updateItemQuantitySucceededPayload,
} from '../payload/cart';

export interface CartState {
  status: 'idle' | 'pending' | 'succeeded' | 'failed';
  error: string | null;
  data: Cart[];
  lastSync: string | null;
}

export type FetchCartRequested = {
  type: typeof actionName.FETCH_CART;
  payload: FetchCartRequestedPayload;
};

export type FetchCartStart = {
  type: typeof actionName.FETCH_CART_START;
  payload: FetchCartStartPayload;
};

export type FetchCartSucceeded = {
  type: typeof actionName.FETCH_CART_SUCCEEDED;
  payload: FetchCartSucceededPayload;
};

export type FetchCartFailed = {
  type: typeof actionName.FETCH_CART_FAILED;
  payload: FetchCartFailedPayload;
};

export type FetchCartClear = {
  type: typeof actionName.FETCH_CART_CLEAR;
  payload: FetchCartClearPayload;
};

export type CreateNewCartRequested = {
  type: typeof actionName.CREATE_NEW_CART;
  payload: CreateNewCartRequestedPayload;
};

export type CreateNewCartStart = {
  type: typeof actionName.CREATE_NEW_CART_START;
  payload: CreateNewCartStartPayload;
};

export type CreateNewCartSucceeded = {
  type: typeof actionName.CREATE_NEW_CART_SUCCEEDED;
  payload: CreateNewCartSucceededPayload;
};

export type CreateNewCartFailed = {
  type: typeof actionName.CREATE_NEW_CART_FAILED;
  payload: CreateNewCartFailedPayload;
};

export type DeleteCartRequested = {
  type: typeof actionName.DELETE_CART;
  payload: DeleteCartRequestedPayload;
};

export type DeleteCartStart = {
  type: typeof actionName.DELETE_CART_START;
  payload: DeleteCartStartPayload;
};

export type DeleteCartSucceeded = {
  type: typeof actionName.DELETE_CART_SUCCEEDED;
  payload: DeleteCartSucceededPayload;
};

export type DeleteCartFailed = {
  type: typeof actionName.DELETE_CART_FAILED;
  payload: DeleteCartFailedPayload;
};

export type AddItemToCartRequested = {
  type: typeof actionName.ADD_ITEM_TO_CART;
  payload: AddItemToCartRequestedPayload;
};

export type AddItemToCartStart = {
  type: typeof actionName.ADD_ITEM_TO_CART_START;
  payload: AddItemToCartStartPayload;
};

export type AddItemToCartSucceeded = {
  type: typeof actionName.ADD_ITEM_TO_CART_SUCCEEDED;
  payload: AddItemToCartSucceededPayload;
};

export type AddItemToCartFailed = {
  type: typeof actionName.ADD_ITEM_TO_CART_FAILED;
  payload: AddItemToCartFailedPayload;
};

export type RemoveItemFromCartRequested = {
  type: typeof actionName.REMOVE_ITEM_FROM_CART;
  payload: RemoveItemFromCartRequestedPayload;
};

export type RemoveItemFromCartStart = {
  type: typeof actionName.REMOVE_ITEM_FROM_CART_START;
  payload: RemoveItemFromCartStartPayload;
};

export type RemoveItemFromCartSucceeded = {
  type: typeof actionName.REMOVE_ITEM_FROM_CART_SUCCEEDED;
  payload: RemoveItemFromCartSucceededPayload;
};

export type RemoveItemFromCartFailed = {
  type: typeof actionName.REMOVE_ITEM_FROM_CART_FAILED;
  payload: RemoveItemFromCartFailedPayload;
};

export type UpdateItemQuantityRequest = {
  type: typeof actionName.UPDATE_ITEM_QUANTITY;
  payload: updateItemQuantityRequestPayload;
};
export type UpdateItemQuantityStart = {
  type: typeof actionName.UPDATE_ITEM_QUANTITY_START;
  payload: updateItemQuantityStartPayload;
};
export type UpdateItemQuantitySucceeded = {
  type: typeof actionName.UPDATE_ITEM_QUANTITY_SUCCEEDED;
  payload: updateItemQuantitySucceededPayload;
};
export type UpdateItemQuantityFailed = {
  type: typeof actionName.UPDATE_ITEM_QUANTITY_FAILED;
  payload: updateItemQuantityFailedPayload;
};
export type UpdateItemQuantityClear = {
  type: typeof actionName.UPDATE_ITEM_QUANTITY_CLEAR;
  payload: updateItemQuantityClearPayload;
};
