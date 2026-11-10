import { Cart, CartItem } from '@/models/types';
import { create } from 'zustand';

interface SelectedCheckoutState {
  selectedCarts: { [cartId: number]: { [itemId: number]: boolean } };
  toggleCartItem: (cartId: number, itemId: number) => void;
  toggleCart: (cartId: number, items: CartItem[]) => void;
  toggleAllCarts: (carts: Cart[]) => void;
  isCartSelected: (cartId: number, items: CartItem[]) => boolean;
  isAllCartsSelected: (carts: Cart[]) => boolean;
  resetSelection: () => void;
  getAllSelectedItems: () => { cartId: number; items: number[] }[];
}

const useSelectedCheckout = create<SelectedCheckoutState>((set, get) => ({
  selectedCarts: {},
  toggleCartItem: (cartId: number, itemId: number) => {
    set((state) => {
      const cartItems = state.selectedCarts[cartId] || {};
      return {
        selectedCarts: { ...state.selectedCarts, [cartId]: { ...cartItems, [itemId]: !cartItems[itemId] } },
      };
    });
  },
  toggleCart: (cartId: number, items: CartItem[]) => {
    set((state) => {
      const cartItems = state.selectedCarts[cartId] || {};
      const allSelected = items.every((item) => cartItems[item.id]);
      return {
        selectedCarts: {
          ...state.selectedCarts,
          [cartId]: items.reduce((acc, item) => ({ ...acc, [item.id]: !allSelected }), {}),
        },
      };
    });
  },
  toggleAllCarts: (carts: Cart[]) => {
    set((state) => {
      const allSelected = carts.every((cart) =>
        cart.cartItems.every((item) => state.selectedCarts[cart.id]?.[item.id])
      );
      return {
        selectedCarts: carts.reduce(
          (acc, cart) => ({
            ...acc,
            [cart.id]: cart.cartItems.reduce((itemAcc, item) => ({ ...itemAcc, [item.id]: !allSelected }), {}),
          }),
          {}
        ),
      };
    });
  },
  isCartSelected: (cartId: number, items: CartItem[]) => {
    const state = get();
    const cartItems = state.selectedCarts[cartId] || {};
    return items.every((item) => cartItems[item.id]);
  },
  isAllCartsSelected: (carts: Cart[]) => {
    const state = get();
    return carts.every((cart) => state.isCartSelected(cart.id, cart.cartItems));
  },
  resetSelection: () => {
    set({ selectedCarts: {} });
  },
  getAllSelectedItems: () => {
    const state = get();
    return Object.entries(state.selectedCarts)
      .map(([cartId, items]) => ({
        cartId: Number(cartId),
        items: Object.entries(items)
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          .filter(([_, isSelected]) => isSelected)
          .map(([itemId]) => Number(itemId)),
      }))
      .filter((cart) => cart.items.length > 0);
  },
}));

export default useSelectedCheckout;
