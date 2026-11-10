import { CartState } from '@/redux/actions/type/cart';
import { PayloadAction, createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import {
  AddItemToCartFailedPayload,
  AddItemToCartStartPayload,
  AddItemToCartSucceededPayload,
  CreateNewCartFailedPayload,
  CreateNewCartStartPayload,
  CreateNewCartSucceededPayload,
  DeleteCartFailedPayload,
  DeleteCartStartPayload,
  DeleteCartSucceededPayload,
  FetchCartFailedPayload,
  FetchCartStartPayload,
  FetchCartSucceededPayload,
  RemoveItemFromCartFailedPayload,
  RemoveItemFromCartStartPayload,
  RemoveItemFromCartSucceededPayload,
  updateItemQuantityFailedPayload,
  updateItemQuantityStartPayload,
  updateItemQuantitySucceededPayload,
} from '@/redux/actions/payload/cart';
import { getCartByCustomerId } from '@/api/cartApi';

const initialState: CartState = {
  status: 'idle',
  data: [],
  error: null,
  lastSync: null,
};

const cartSlice = createSlice({
  name: 'cart',
  initialState: initialState,
  reducers: {
    fetchCartStart: (state: CartState, action: PayloadAction<FetchCartStartPayload>) => {
      state.status = 'pending';
    },
    fetchCartSucceeded: (state: CartState, action: PayloadAction<FetchCartSucceededPayload>) => {
      state.status = 'succeeded';
      state.data = action.payload.data;
    },
    fetchCartFailed: (state: CartState, action: PayloadAction<FetchCartFailedPayload>) => {
      state.status = 'failed';
      state.error = action.payload.error;
    },
    fetchCartClear: (state: CartState, action: PayloadAction<FetchCartFailedPayload>) => {
      state.status = 'idle';
    },
    createNewCartStart: (state: CartState, action: PayloadAction<CreateNewCartStartPayload>) => {
      state.status = 'pending';
    },
    createNewCartSucceeded: (state: CartState, action: PayloadAction<CreateNewCartSucceededPayload>) => {
      state.status = 'succeeded';
      state.data.push(action.payload.data);
    },
    createNewCartFailed: (state: CartState, action: PayloadAction<CreateNewCartFailedPayload>) => {
      state.status = 'failed';
      state.error = action.payload.error;
    },
    createNewCartClear: (state: CartState) => {
      state.status = 'idle';
    },
    deleteCartStart: (state: CartState, action: PayloadAction<DeleteCartStartPayload>) => {
      state.status = 'pending';
    },
    deleteCartSucceeded: (state: CartState, action: PayloadAction<DeleteCartSucceededPayload>) => {
      state.status = 'succeeded';
      state.data = state.data.filter((cart) => cart.id != action.payload.cartId);
    },
    deleteCartFailed: (state: CartState, action: PayloadAction<DeleteCartFailedPayload>) => {
      state.status = 'failed';
      state.error = action.payload.error;
    },
    deleteCartClear: (state: CartState) => {
      state.status = 'idle';
    },
    addItemToCartStart: (state: CartState, action: PayloadAction<AddItemToCartStartPayload>) => {
      state.status = 'pending';
    },
    addItemToCartSucceeded: (state: CartState, action: PayloadAction<AddItemToCartSucceededPayload>) => {
      const cart = state.data.find((c) => c.id === action.payload.item.cart);
      if (cart) {
        const existingItem = cart.cartItems.find((item) => item.product === action.payload.item.product);
        if (existingItem) {
          existingItem.quantity += action.payload.item.quantity;
        } else {
          cart.cartItems.push(action.payload.item);
        }
      }
      state.status = 'succeeded';
    },
    addItemToCartFailed: (state: CartState, action: PayloadAction<AddItemToCartFailedPayload>) => {
      state.status = 'failed';
      state.error = action.payload.error;
    },
    removeItemFromCartStart: (state: CartState, action: PayloadAction<RemoveItemFromCartStartPayload>) => {
      state.status = 'pending';
    },
    removeItemFromCartSucceeded: (state: CartState, action: PayloadAction<RemoveItemFromCartSucceededPayload>) => {
      state.status = 'succeeded';
      state.data = action.payload;
    },
    removeItemFromCartFailed: (state: CartState, action: PayloadAction<RemoveItemFromCartFailedPayload>) => {
      state.status = 'failed';
      state.error = action.payload.error;
    },
    updateItemQuantityStart: (state: CartState, action: PayloadAction<updateItemQuantityStartPayload>) => {
      state.status = 'pending';
    },
    updateItemQuantitySucceeded: (state: CartState, action: PayloadAction<updateItemQuantitySucceededPayload>) => {
      state.status = 'succeeded';
      state.data = action.payload.data;
    },
    updateItemQuantityFailed: (state: CartState, action: PayloadAction<updateItemQuantityFailedPayload>) => {
      state.status = 'failed';
      state.error = action.payload.error;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(initialCart.pending, (state) => {
      state.status = 'pending';
    })
    .addCase(initialCart.fulfilled, (state, action: PayloadAction<FetchCartSucceededPayload>) => {
      state.status = 'succeeded';
      state.data = action.payload.data;
    })
    .addCase(initialCart.rejected, (state, action) => {
      state.status = 'failed';
      state.error = action.error.message ?? "Failed to fetch cart";
    });
  },
});

export const {
  fetchCartStart,
  fetchCartSucceeded,
  fetchCartFailed,
  fetchCartClear,
  createNewCartStart,
  createNewCartSucceeded,
  createNewCartFailed,
  createNewCartClear,
  deleteCartStart,
  deleteCartSucceeded,
  deleteCartFailed,
  deleteCartClear,
  addItemToCartStart,
  addItemToCartSucceeded,
  addItemToCartFailed,
  removeItemFromCartStart,
  removeItemFromCartSucceeded,
  removeItemFromCartFailed,
  updateItemQuantityStart,
  updateItemQuantitySucceeded,
  updateItemQuantityFailed,
} = cartSlice.actions;
export default cartSlice.reducer;

export const initialCart = createAsyncThunk('cart/initialCart', async (customerId: string) => {
  return await getCartByCustomerId(customerId);
});
