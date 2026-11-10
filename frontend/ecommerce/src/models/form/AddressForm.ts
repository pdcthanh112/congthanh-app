export type CreateAddressForm = {
  customer: string;
  fullName: string;
  phone: string;
  label: 'HOME' | 'OFFICE';
  country: string;
  addressLine1: string;
  addressLine2: string;
  addressLine3: string;
  street: string;
  postalCode: string;
  longitude: number;
  latitude: number;
  isDefault: boolean;
};

export type UpdateAddressForm = {
  id: number;
  phone: string;
  country: string;
  addressLine1: string;
  addressLine2: string;
  addressLine3: string;
  street: string;
  postalCode: string;
};
