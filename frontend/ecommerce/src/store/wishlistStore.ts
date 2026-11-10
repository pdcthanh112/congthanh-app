import { create } from 'zustand';

interface WishlistStore {
  items: string[];
  setWishlist: (items: string[]) => void;
  addToWishlist: (productId: string) => void;
  removeFromWishlist: (productId: string) => void;
  checkExistItemInWishlist: (productId: string) => boolean;
}

export const useWishlistStore = create<WishlistStore>((set, get) => ({
  items: [],
  setWishlist: (items) => set({ items }),
  addToWishlist: (productId) =>
    set((state) => ({
      items: [...state.items, productId],
    })),
  removeFromWishlist: (productId) =>
    set((state) => ({
      items: state.items.filter((item) => item !== productId),
    })),
  checkExistItemInWishlist: (productId) => {
    return get().items.find((item) => item === productId) === undefined;
  },
}));
