import { createAction } from '@reduxjs/toolkit';

export const FETCH_CART = createAction('cart/fetchCart');
export const FETCH_CART_START = createAction('cart/fetchCartStart');
export const FETCH_CART_SUCCEEDED = createAction('cart/fetchCartSucceeded');
export const FETCH_CART_FAILED = createAction('cart/fetchCartFailed');
export const FETCH_CART_CLEAR = createAction('cart/fetchCartClear');

export const CREATE_NEW_CART = createAction('cart/createNewCart');
export const CREATE_NEW_CART_START = createAction('cart/createNewCartStart');
export const CREATE_NEW_CART_SUCCEEDED = createAction('cart/createNewCartSucceeded');
export const CREATE_NEW_CART_FAILED = createAction('cart/createNewCartFailed');

export const DELETE_CART = createAction('cart/deleteCart');
export const DELETE_CART_START = createAction('cart/deleteCartStart');
export const DELETE_CART_SUCCEEDED = createAction('cart/deleteCartSucceeded');
export const DELETE_CART_FAILED = createAction('cart/deleteCartFailed');

export const ADD_ITEM_TO_CART = createAction('cart/addItemToCart');
export const ADD_ITEM_TO_CART_START = createAction('cart/addItemToCartStart');
export const ADD_ITEM_TO_CART_SUCCEEDED = createAction('cart/addItemToCartSucceeded');
export const ADD_ITEM_TO_CART_FAILED = createAction('cart/addItemToCartFailed');

export const REMOVE_ITEM_FROM_CART = createAction('cart/removeItemFromCart');
export const REMOVE_ITEM_FROM_CART_START = createAction('cart/removeItemFromCartStart');
export const REMOVE_ITEM_FROM_CART_SUCCEEDED = createAction('cart/removeItemFromCartSucceeded');
export const REMOVE_ITEM_FROM_CART_FAILED = createAction('cart/removeItemFromCartFailed');

export const UPDATE_ITEM_QUANTITY = createAction('cart/updateItemQuantity')
export const UPDATE_ITEM_QUANTITY_START = createAction('cart/updateItemQuantityStart')
export const UPDATE_ITEM_QUANTITY_SUCCEEDED = createAction('cart/updateItemQuantitySucceeded')
export const UPDATE_ITEM_QUANTITY_FAILED = createAction('cart/updateItemQuantityFailed')
export const UPDATE_ITEM_QUANTITY_CLEAR = createAction('cart/updateItemQuantityClear')
