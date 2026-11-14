'use client';

import { addProductToCart, clearCart, deleteCartItem, getMyCart, updateCartItem } from '@/api/cartApi';
import type { Cart } from '@/graphql/types';
import { CART_KEY } from '@/utils/constants/queryKey';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSession } from 'next-auth/react';

export const useMyCart = () => {
  const { status } = useSession();
  return useQuery({
    queryKey: [CART_KEY],
    queryFn: getMyCart,
    enabled: status === 'authenticated',
    staleTime: 30_000,
  });
};

const useCartMutation = <TVars>(fn: (vars: TVars) => Promise<Cart>) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [CART_KEY],
    mutationFn: fn,
    onSuccess: (cart) => queryClient.setQueryData([CART_KEY], cart),
  });
};

export const useAddProductToCart = () =>
  useCartMutation((data: { productId: string; quantity: number }) => addProductToCart(data));

export const useUpdateCartItem = () =>
  useCartMutation((data: { itemId: string; quantity: number }) => updateCartItem(data));

export const useDeleteCartItem = () => useCartMutation((itemId: string) => deleteCartItem(itemId));

export const useClearCart = () => useCartMutation((_: void) => clearCart());
