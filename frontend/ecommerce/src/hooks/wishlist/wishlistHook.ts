import { useMutation, useQueryClient } from '@tanstack/react-query';
import { addProductToWishlist, removeProductFromWishlist } from '@/api/wishlistApi';

export const useAddProductToWishlist = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (data: { customerId: string; productId: string }) =>
      await addProductToWishlist(data.customerId, data.productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    },
  });
};

export const useRemoveProductFromWishlist = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (data: { customerId: string; productId: string }) =>
      await removeProductFromWishlist(data.customerId, data.productId),

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    },
  });
};
