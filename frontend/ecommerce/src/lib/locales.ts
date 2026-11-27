import { defaultLocale, locales, type Locale } from '@/i18n/routing';

export { defaultLocale, locales, type Locale };

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
  ja: '日本語',
  ar: 'العربية',
  pt: 'Português',
  nl: 'Nederlands',
  nb: 'Norsk bokmål',
  tr: 'Türkçe',
};

/** Ngôn ngữ viết từ phải sang trái; layout gốc đặt `dir="rtl"` cho các locale này. */
const RTL_LOCALES: ReadonlySet<Locale> = new Set<Locale>(['ar']);

/** Hướng chữ của locale, dùng cho thuộc tính `dir` của thẻ html. */
export function localeDirection(locale: Locale): 'ltr' | 'rtl' {
  return RTL_LOCALES.has(locale) ? 'rtl' : 'ltr';
}

export function isValidLocale(locale: string): locale is Locale {
  return (locales as readonly string[]).includes(locale);
}

/** Bỏ prefix locale khỏi pathname: /vi/manage/profile → /manage/profile. */
export function stripLocale(pathname: string): string {
  const [, first, ...rest] = pathname.split('/');
  return isValidLocale(first ?? '') ? `/${rest.join('/')}` : pathname;
}
