export const formatDateTime = (value: string | number | Date, locale: string, opts: Intl.DateTimeFormatOptions = {}) =>
  new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', ...opts }).format(new Date(value));

export const formatDate = (value: string | number | Date, locale: string) =>
  new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(new Date(value));

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
];

/** "3 giờ trước", "in 2 days"... */
export function formatRelative(value: string | number | Date, locale: string, now = Date.now()): string {
  const seconds = Math.round((new Date(value).getTime() - now) / 1000);
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) return rtf.format(Math.round(seconds / size), unit);
  }
  return rtf.format(seconds, 'second');
}
