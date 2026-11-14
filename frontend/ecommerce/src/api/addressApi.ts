import { gql } from '@/graphql/client';
import {
  AddAddressMutation,
  DeleteAddressMutation,
  MyAddressesQuery,
  SetDefaultAddressMutation,
  UpdateAddressMutation,
} from '@/graphql/documents';
import type { AddressInput, ShippingAddress } from '@/graphql/types';

// Địa chỉ giao hàng (customer-service, qua supergraph) của khách đang đăng nhập, xác định theo token.
export const getMyAddresses = async (): Promise<{ addresses: ShippingAddress[]; defaultAddress: ShippingAddress | null }> => {
  const { me } = await gql(MyAddressesQuery);
  return { addresses: me.shippingAddresses, defaultAddress: me.defaultShippingAddress };
};

export const createAddress = async (input: AddressInput, makeDefault = false) =>
  (await gql(AddAddressMutation, { input, makeDefault })).addShippingAddress;

export const updateAddress = async (id: string, input: AddressInput) =>
  (await gql(UpdateAddressMutation, { id, input })).updateShippingAddress;

export const deleteAddress = async (id: string) => (await gql(DeleteAddressMutation, { id })).deleteShippingAddress;

export const setDefaultAddress = async (id: string) =>
  (await gql(SetDefaultAddressMutation, { id })).setDefaultShippingAddress;

/** Một dòng địa chỉ để hiển thị. */
export const formatAddress = (a: ShippingAddress) =>
  [a.addressLine, a.wardCode, a.districtCode, a.provinceCode, a.countryCode].filter(Boolean).join(', ');
