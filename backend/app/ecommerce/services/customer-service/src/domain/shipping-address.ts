import { DomainError } from '@ecommerce/platform/errors';

export type AddressLabel = 'HOME' | 'OFFICE' | 'OTHER';
export const MAX_ADDRESSES_PER_CUSTOMER = 10;

export interface ShippingAddressProps {
  id: string;
  customerId: string;
  fullName: string;
  phoneNumber: string;
  label: AddressLabel;
  countryCode: string;
  provinceCode: string | null;
  districtCode: string | null;
  wardCode: string | null;
  addressLine: string;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  isDefault: boolean;
  createdAt: Date;
  updatedAt: Date;
}

export type AddressDraft = Omit<ShippingAddressProps, 'id' | 'customerId' | 'isDefault' | 'createdAt' | 'updatedAt'>;

const PHONE = /^\+?[0-9]{8,15}$/;

/** Kiểm tra và chuẩn hoá dữ liệu địa chỉ (bất biến: tên, số điện thoại, dòng địa chỉ, mã nước hợp lệ). */
export function normalizeAddress(d: AddressDraft): AddressDraft {
  const fullName = d.fullName?.trim();
  if (!fullName) throw DomainError.invalid('fullName is required');
  const phoneNumber = d.phoneNumber?.replace(/[\s.-]/g, '');
  if (!phoneNumber || !PHONE.test(phoneNumber)) throw DomainError.invalid('phoneNumber is invalid', 'INVALID_PHONE');
  const addressLine = d.addressLine?.trim();
  if (!addressLine) throw DomainError.invalid('addressLine is required');
  const countryCode = d.countryCode?.trim().toUpperCase();
  if (!/^[A-Z]{2}$/.test(countryCode ?? '')) throw DomainError.invalid('countryCode must be ISO 3166-1 alpha-2');
  if ((d.latitude == null) !== (d.longitude == null)) throw DomainError.invalid('latitude and longitude go together');
  if (d.latitude != null && (Math.abs(d.latitude) > 90 || Math.abs(d.longitude!) > 180)) {
    throw DomainError.invalid('coordinates out of range');
  }
  return { ...d, fullName, phoneNumber, addressLine, countryCode };
}

/**
 * Quy tắc "đúng một địa chỉ mặc định": địa chỉ đầu tiên luôn mặc định; xoá địa chỉ mặc định thì
 * địa chỉ mới nhất còn lại thành mặc định. Trả về id cần đặt mặc định (hoặc undefined nếu không còn).
 */
export function nextDefaultAfterDelete(remaining: readonly ShippingAddressProps[]): string | undefined {
  return [...remaining].sort((a, b) => b.updatedAt.getTime() - a.updatedAt.getTime())[0]?.id;
}
