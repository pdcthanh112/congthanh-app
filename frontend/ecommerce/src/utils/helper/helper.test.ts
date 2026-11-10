import { formatNumber } from './';

describe('roundNumber', () => {
  it('round input number', () => {
    expect(formatNumber(123)).toBe('123');
    expect(formatNumber(1000.23)).toBe('1000.2k');
    expect(formatNumber(12345.678)).toBe('12345.7k');
  });
});
