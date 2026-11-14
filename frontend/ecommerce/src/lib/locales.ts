import { defaultLocale, locales, type Locale } from '@/i18n/routing';

export { defaultLocale, locales, type Locale };

/** Tên hiển thị của từng ngôn ngữ trong bộ chọn ngôn ngữ. */
export const localeNames: Record<Locale, string> = {
  en: 'English',
  vi: 'Tiếng Việt',
  es: 'Español',
  de: 'Deutsch',
  fr: 'Français',
  it: 'Italiano',
  ko: '한국어',
  ru: 'Русский',
  zh: '中文',
};

export function isValidLocale(locale: string): locale is Locale {
  return (locales as readonly string[]).includes(locale);
}

/** Bỏ prefix locale khỏi pathname: /vi/manage/profile → /manage/profile. */
export function stripLocale(pathname: string): string {
  const [, first, ...rest] = pathname.split('/');
  return isValidLocale(first ?? '') ? `/${rest.join('/')}` : pathname;
}
