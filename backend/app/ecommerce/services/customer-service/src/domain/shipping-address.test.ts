import { describe, expect, it } from 'vitest';
import { normalizeKeyword } from './search-history.js';
import {
  type AddressDraft,
  nextDefaultAfterDelete,
  normalizeAddress,
  type ShippingAddressProps,
} from './shipping-address.js';

const draft: AddressDraft = {
  fullName: ' Alex Johnson ',
  phoneNumber: '+1 212-555.0100',
  label: 'HOME',
  countryCode: 'us',
  provinceCode: 'NY',
  districtCode: null,
  wardCode: null,
  addressLine: ' 350 5th Ave, Apt 4B ',
  postalCode: '10118',
  latitude: null,
  longitude: null,
};

describe('normalizeAddress', () => {
  it('chuẩn hoá tên, số điện thoại, mã nước', () => {
    expect(normalizeAddress(draft)).toMatchObject({
      fullName: 'Alex Johnson',
      phoneNumber: '+12125550100',
      countryCode: 'US',
      addressLine: '350 5th Ave, Apt 4B',
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
