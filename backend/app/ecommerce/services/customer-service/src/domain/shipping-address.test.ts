import { describe, expect, it } from 'vitest';
import { normalizeKeyword } from './search-history.js';
import {
  type AddressDraft,
  nextDefaultAfterDelete,
  normalizeAddress,
  type ShippingAddressProps,
} from './shipping-address.js';

const draft: AddressDraft = {
  fullName: ' Nguyễn Văn A ',
  phoneNumber: '090 123.4567',
  label: 'HOME',
  countryCode: 'vn',
  provinceCode: '79',
  districtCode: null,
  wardCode: null,
  addressLine: ' 1 Lê Lợi, Quận 1 ',
  postalCode: null,
  latitude: null,
  longitude: null,
};

describe('normalizeAddress', () => {
  it('chuẩn hoá tên, số điện thoại, mã nước', () => {
    expect(normalizeAddress(draft)).toMatchObject({
      fullName: 'Nguyễn Văn A',
      phoneNumber: '0901234567',
      countryCode: 'VN',
      addressLine: '1 Lê Lợi, Quận 1',
    });
  });
  it('từ chối số điện thoại sai', () => {
    expect(() => normalizeAddress({ ...draft, phoneNumber: 'abc' })).toThrow(/phoneNumber/);
  });
  it('toạ độ phải đi cặp', () => {
    expect(() => normalizeAddress({ ...draft, latitude: 10 })).toThrow(/together/);
  });
});

describe('nextDefaultAfterDelete', () => {
  it('chọn địa chỉ sửa gần nhất', () => {
    const at = (id: string, t: number) => ({ id, updatedAt: new Date(t) }) as ShippingAddressProps;
    expect(nextDefaultAfterDelete([at('a', 1), at('b', 3), at('c', 2)])).toBe('b');
    expect(nextDefaultAfterDelete([])).toBeUndefined();
  });
});

describe('normalizeKeyword', () => {
  it('gộp khoảng trắng', () => {
    expect(normalizeKeyword('  áo   thun ')).toBe('áo thun');
    expect(() => normalizeKeyword('  ')).toThrow();
  });
});
