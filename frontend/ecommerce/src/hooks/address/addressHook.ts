import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createAddress, deleteAddress, updateAddress } from '@/api/addressApi';
import { CreateAddressForm, UpdateAddressForm } from '@/models/form';
import { ADDRESS_KEY } from '@/utils/constants/queryKey';

export const useCreateAddress = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [ADDRESS_KEY],
    mutationFn: async (data: CreateAddressForm) => await createAddress(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['address'] });
    },
  });
};

export const useUpdateAddress = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [ADDRESS_KEY],
    mutationFn: async (data: UpdateAddressForm) => await updateAddress(data.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['address'] });
    },
  });
};

export const useDeleteAddress = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [ADDRESS_KEY],
    mutationFn: async (addressId: number) => await deleteAddress(addressId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['address'] });
    },
  });
};
