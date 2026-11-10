import { gql } from '@/graphql/client';
import {
  AddAddressMutation,
  DeleteAddressMutation,
  MyAddressesQuery,
  UpdateAddressMutation,
} from '@/graphql/documents';
import { ok, toAddress } from '@/graphql/legacy';
import type { AddressInput } from '@/graphql/types';
import { CreateAddressForm, UpdateAddressForm } from '@/models/form';
import { Address, Response } from '@/models/types';

// Địa chỉ giao hàng (customer-service, qua supergraph) của khách đang đăng nhập.
type Id = string | number;

const toInput = (d: UpdateAddressForm | CreateAddressForm): AddressInput => {
  const f = d as Partial<CreateAddressForm> & { fullName?: string };
  return {
    fullName: f.fullName ?? '',
    phoneNumber: String(f.phone ?? ''),
    addressLine: [f.addressLine1, f.street].filter(Boolean).join(', '),
    wardCode: f.addressLine2 || null,
    districtCode: f.addressLine3 || null,
    postalCode: f.postalCode ? String(f.postalCode) : null,
    countryCode: f.country || 'VN',
  };
};

export const getAddressByCustomer = async (customerId: string): Promise<Response<Address[]>> => {
  const { me } = await gql(MyAddressesQuery);
  return ok(me.shippingAddresses.map((a) => toAddress(a, customerId)));
};

export const getAddressById = async (addressId: Id): Promise<Response<Address | null>> => {
  const { me } = await gql(MyAddressesQuery);
  const a = me.shippingAddresses.find((x) => x.id === String(addressId));
  return ok(a ? toAddress(a, me.id) : null);
};

export const getDefaultAddressOfCustomer = async (customerId: string): Promise<Response<Address | null>> => {
  const { me } = await gql(MyAddressesQuery);
  return ok(me.defaultShippingAddress ? toAddress(me.defaultShippingAddress, customerId) : null);
};

export const createAddress = async (data: CreateAddressForm) => {
  const { addShippingAddress } = await gql(AddAddressMutation, {
    input: toInput(data),
    makeDefault: Boolean((data as { isDefault?: boolean }).isDefault),
  });
  return ok(toAddress(addShippingAddress));
};

export const updateAddress = async (addressId: Id, data: UpdateAddressForm) => {
  const { updateShippingAddress } = await gql(UpdateAddressMutation, { id: String(addressId), input: toInput(data) });
  return ok(toAddress(updateShippingAddress));
};

export const deleteAddress = async (addressId: Id) =>
  ok((await gql(DeleteAddressMutation, { id: String(addressId) })).deleteShippingAddress);
