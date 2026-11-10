import { describe, expect, it } from 'vitest';
import { nextAttemptDelayMs } from './notification.js';
import { channelsFor, defaultPreferences, inQuietHours, keyOf } from './preferences.js';
import { formatMoney } from './templates.js';

// 16:30 UTC = 23:30 giờ Việt Nam
const lateNight = new Date('2026-05-01T16:30:00Z');
const afternoon = new Date('2026-05-01T07:00:00Z');
const qh = { start: '22:00', end: '07:00', timezone: 'Asia/Ho_Chi_Minh' };

describe('giờ yên tĩnh', () => {
  it('khoảng qua đêm theo múi giờ người dùng', () => {
    expect(inQuietHours(qh, lateNight)).toBe(true);
    expect(inQuietHours(qh, afternoon)).toBe(false);
    expect(inQuietHours({ ...qh, start: '13:00', end: '15:00' }, afternoon)).toBe(true);
  });
});

describe('chọn kênh', () => {
  const all = ['IN_APP', 'EMAIL', 'SMS', 'WEB_PUSH', 'FCM'] as const;

  it('mặc định: SMS chỉ cho giao dịch', () => {
    const p = defaultPreferences('u');
    expect(channelsFor(p, 'ORDER', all, afternoon)).toEqual([...all]);
    expect(channelsFor(p, 'WISHLIST', all, afternoon)).toEqual(['IN_APP', 'EMAIL', 'WEB_PUSH', 'FCM']);
  });

  it('không tắt được in-app/email của đơn hàng; giờ yên tĩnh chặn push cho khuyến mãi, không chặn đơn hàng', () => {
    const p = {
      ...defaultPreferences('u'),
      disabled: new Set([keyOf('ORDER', 'EMAIL'), keyOf('ORDER', 'SMS')]),
      quietHours: qh,
    };
    expect(channelsFor(p, 'ORDER', all, lateNight)).toEqual(['IN_APP', 'EMAIL', 'WEB_PUSH', 'FCM']);
    expect(channelsFor(p, 'PROMOTION', all, lateNight)).toEqual(['IN_APP', 'EMAIL']);
  });
});

describe('khác', () => {
  it('backoff tăng dần có trần', () => {
    expect(nextAttemptDelayMs(1)).toBe(30_000);
    expect(nextAttemptDelayMs(2)).toBe(120_000);
    expect(nextAttemptDelayMs(10)).toBe(7_200_000);
  });
  it('định dạng tiền VND', () => {
    expect(formatMoney({ currencyCode: 'VND', units: 1250000n, nanos: 0 }).replace(/\s/g, ' ')).toBe('1.250.000 ₫');
  });
});
