import { put, takeEvery } from 'redux-saga/effects';
import * as actionName from '../actions/name/cart';
import { PayloadAction } from '@reduxjs/toolkit';
import {
  addItemToCartFailed,
  addItemToCartStart,
  addItemToCartSucceeded,
  createNewCartClear,
  createNewCartFailed,
  createNewCartStart,
  createNewCartSucceeded,
  deleteCartClear,
  deleteCartFailed,
  deleteCartStart,
  deleteCartSucceeded,
  fetchCartClear,
  fetchCartFailed,
  fetchCartStart,
  fetchCartSucceeded,
  removeItemFromCartFailed,
  removeItemFromCartStart,
  removeItemFromCartSucceeded,
  updateItemQuantityFailed,
  updateItemQuantityStart,
  updateItemQuantitySucceeded,
} from '@/redux/reducers/cartReducer';
import * as cartApi from '@/api/cartApi';

function* fetchCart(action: PayloadAction<any>) {
  try {
    yield put(fetchCartStart(action.payload));
    const { data } = yield cartApi.getCartByCustomerId(action.payload.params.customerId);
    yield put(fetchCartSucceeded({ data: data }));
  } catch (error) {
    yield put(fetchCartFailed({ error: 'Error:' + error }));
  } finally {
    yield put(fetchCartClear(action.payload));
  }
}

function* createNewCart(action: ReturnType<typeof createNewCartStart>) {
  try {
    yield put(createNewCartStart(action.payload));
    const { data } = yield cartApi.createNewCart({
      name: action.payload.name,
      customer: action.payload.customer,
      isDefault: action.payload.isDefault,
    });
    yield put(createNewCartSucceeded({ data }));
  } catch (error) {
    yield put(createNewCartFailed({ error: 'Error: ' + error }));
  } finally {
    yield put(createNewCartClear());
  }
}

function* deleteCart(action: ReturnType<typeof deleteCartStart>) {
  try {
    yield put(deleteCartStart({cartId: action.payload.cartId}));
    yield cartApi.deleteCart(action.payload.cartId);
    yield put(deleteCartSucceeded({data: []}));
  } catch (error) {
    yield put(deleteCartFailed({ error: 'Error: ' + error }));
  } finally {
    yield put(deleteCartClear());
  }
}

function* addToCart(action: PayloadAction<any>) {
  try {
    yield put(addItemToCartStart(action.payload));
    yield cartApi.addProductToCart({
      productId: action.payload.productId,
      quantity: action.payload.quantity,
      cartId: action.payload.cartId,
    });
    yield put(addItemToCartSucceeded(action.payload));
  } catch (error) {
    yield put(addItemToCartFailed({ error: 'Error' + error }));
  }
}

function* removeFromCart(action: PayloadAction<any>) {
  try {
    yield put(removeItemFromCartStart(action.payload));
    yield cartApi.deleteCartItem(action.payload);
    yield put(removeItemFromCartSucceeded(action.payload));
  } catch (error) {
    yield put(removeItemFromCartFailed({ error: 'Error' + error }));
  }
}

function* updateItemQuantity(action: PayloadAction<any>) {
  try {
    yield put(updateItemQuantityStart(action.payload));
    yield cartApi.updateCartItem({ cartItemId: action.payload.itemId, quantity: action.payload.quantity });
    yield put(updateItemQuantitySucceeded(action.payload));
  } catch (error) {
    yield put(updateItemQuantityFailed({ error: 'Error' + error }));
  }
}

export function* cartSaga() {
  yield takeEvery(actionName.FETCH_CART, fetchCart);
  yield takeEvery(actionName.CREATE_NEW_CART, createNewCart);
  yield takeEvery(actionName.DELETE_CART, deleteCart);
  yield takeEvery(actionName.ADD_ITEM_TO_CART, addToCart);
  yield takeEvery(actionName.REMOVE_ITEM_FROM_CART, removeFromCart);
  yield takeEvery(actionName.UPDATE_ITEM_QUANTITY, updateItemQuantity);
}
