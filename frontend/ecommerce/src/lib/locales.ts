export const locales = ["en", "es", "vi"] as const;
export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = 'en';

export function isValidLocale(locale: string): locale is Locale {
  return locales.includes(locale as Locale);
}

// Format số, tiền tệ theo locale
export const formatters = {
  number: new Intl.NumberFormat('vi-VN'),
  currency: new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }),
  // Thêm các formatter khác nếu cần
};