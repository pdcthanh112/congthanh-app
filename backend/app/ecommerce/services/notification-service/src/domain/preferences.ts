import { DomainError } from '@ecommerce/platform/errors';
import { CATEGORIES, type Category, type Channel, TRANSACTIONAL } from './notification.js';

export interface QuietHours {
  /** "22:00" */
  start: string;
  /** "07:00" (qua ngày được) */
  end: string;
  /** IANA, ví dụ America/New_York */
  timezone: string;
}

export interface Preferences {
  userId: string;
  /** Các cặp bị tắt, dạng "PROMOTION:SMS". Mặc định bật hết trừ SMS cho nhóm không giao dịch. */
  disabled: ReadonlySet<string>;
  quietHours: QuietHours | null;
}

export const keyOf = (category: Category, channel: Channel) => `${category}:${channel}`;

/** Mặc định: SMS chỉ cho giao dịch (tốn tiền), còn lại bật. */
export function defaultPreferences(userId: string): Preferences {
  const disabled = new Set(CATEGORIES.filter((c) => !TRANSACTIONAL.has(c)).map((c) => keyOf(c, 'SMS')));
  return { userId, disabled, quietHours: null };
}

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

export function validateQuietHours(q: QuietHours | null): QuietHours | null {
  if (!q) return null;
  if (!HHMM.test(q.start) || !HHMM.test(q.end)) throw DomainError.invalid('quiet hours must be HH:mm');
  try {
    new Intl.DateTimeFormat('en', { timeZone: q.timezone });
  } catch {
    throw DomainError.invalid(`unknown timezone ${q.timezone}`);
  }
  return q;
}

/** Bây giờ có nằm trong giờ yên tĩnh của người dùng không (theo múi giờ của họ). */
export function inQuietHours(q: QuietHours | null, now: Date): boolean {
  if (!q) return false;
  const local = new Intl.DateTimeFormat('en-GB', {
    timeZone: q.timezone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(now);
  const [s, e, n] = [q.start, q.end, local];
  return s <= e ? n >= s && n < e : n >= s || n < e;
}

/**
 * Kênh sẽ gửi cho một thông báo: bỏ kênh người dùng tắt (trừ nhóm giao dịch với IN_APP/EMAIL),
 * và trong giờ yên tĩnh bỏ các kênh làm phiền (SMS/push) cho nhóm không giao dịch.
 */
export function channelsFor(p: Preferences, category: Category, wanted: readonly Channel[], now: Date): Channel[] {
  const transactional = TRANSACTIONAL.has(category);
  const quiet = !transactional && inQuietHours(p.quietHours, now);
  return wanted.filter((ch) => {
    if (transactional && (ch === 'IN_APP' || ch === 'EMAIL')) return true;
    if (p.disabled.has(keyOf(category, ch))) return false;
    if (quiet && (ch === 'SMS' || ch === 'WEB_PUSH' || ch === 'FCM')) return false;
    return true;
  });
}
