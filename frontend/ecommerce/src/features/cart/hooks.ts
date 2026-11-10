import { AddToCartForm, CreateCartForm, UpdateCartItemForm } from '@/models/form';
import { CART_KEY } from '@/utils/constants/queryKey';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { addProductToCart, createNewCart, deleteCart, deleteCartItem, updateCartItem } from '@/api/cartApi';

export const useCreateNewCart = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [CART_KEY],
    mutationFn: async (data: CreateCartForm) => await createNewCart(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CART_KEY] });
    },
  });
};

export const useDeleteCart = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [CART_KEY],
    mutationFn: async (cartId: number) => await deleteCart(cartId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CART_KEY] });
    },
  });
};

export const useAddProductToCart = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [CART_KEY],
    mutationFn: async (data: AddToCartForm) =>
      await addProductToCart({ productId: data.productId, quantity: data.quantity, cartId: data.cartId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CART_KEY] });
    },
  });
};

export const useUpdateCartItem = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [CART_KEY],
    mutationFn: async (data: UpdateCartItemForm) =>
      await updateCartItem({ cartItemId: data.itemId, quantity: data.quantity }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CART_KEY] });
    },
  });
};

export const useDeleteCartItem = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [CART_KEY],
    mutationFn: async (itemId: number) => await deleteCartItem(itemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CART_KEY] });
    },
  });
};
