'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSession } from 'next-auth/react';
import { addProductToWishlist, getMyWishlist, removeProductFromWishlist } from '@/api/wishlistApi';
import { WISHLIST_KEY } from '@/utils/constants/queryKey';

/** Wishlist của khách đang đăng nhập: một query dùng chung cho cả trang (không gọi lại theo từng sản phẩm). */
export const useWishlist = () => {
  const { status } = useSession();
  return useQuery({
    queryKey: [WISHLIST_KEY],
    queryFn: async () => (await getMyWishlist()).data,
    enabled: status === 'authenticated',
  });
};

/** Tập id sản phẩm trong wishlist để tra nhanh trên danh sách. */
export const useWishlistIds = () => {
  const { data } = useWishlist();
  return new Set(data?.product.map((p) => p.id) ?? []);
};

export const useAddProductToWishlist = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (productId: string) => await addProductToWishlist(productId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [WISHLIST_KEY] }),
  });
};

export const useRemoveProductFromWishlist = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (productId: string) => await removeProductFromWishlist(productId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [WISHLIST_KEY] }),
  });
};
