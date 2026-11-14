'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSession } from 'next-auth/react';
import { createAddress, deleteAddress, getMyAddresses, setDefaultAddress, updateAddress } from '@/api/addressApi';
import type { AddressInput } from '@/graphql/types';
import { ADDRESS_KEY } from '@/utils/constants/queryKey';

export const useMyAddresses = () => {
  const { status } = useSession();
  return useQuery({ queryKey: [ADDRESS_KEY], queryFn: getMyAddresses, enabled: status === 'authenticated' });
};

const useAddressMutation = <TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [ADDRESS_KEY],
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [ADDRESS_KEY] }),
  });
};

export const useCreateAddress = () =>
  useAddressMutation(({ input, makeDefault }: { input: AddressInput; makeDefault?: boolean }) =>
    createAddress(input, makeDefault)
  );
export const useUpdateAddress = () =>
  useAddressMutation(({ id, input }: { id: string; input: AddressInput }) => updateAddress(id, input));
export const useDeleteAddress = () => useAddressMutation((id: string) => deleteAddress(id));
export const useSetDefaultAddress = () => useAddressMutation((id: string) => setDefaultAddress(id));
