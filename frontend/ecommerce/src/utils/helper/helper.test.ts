import { formatNumber } from './';

describe('formatNumber', () => {
  it('rút gọn số lớn theo ký hiệu compact', () => {
    expect(formatNumber(123)).toBe('123');
    expect(formatNumber(1000.23)).toBe('1K');
    expect(formatNumber(12345.678)).toBe('12.3K');
  });
});
