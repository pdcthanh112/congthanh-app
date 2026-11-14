import { createNavigation } from 'next-intl/navigation';
import { defineRouting } from 'next-intl/routing';

/** Nguồn sự thật duy nhất cho danh sách ngôn ngữ (mỗi locale có src/locales/<locale>/message.json). */
export const locales = ['en', 'vi', 'es', 'de', 'fr', 'it', 'ko', 'ru', 'zh'] as const;
export type Locale = (typeof locales)[number];
export const defaultLocale: Locale = 'en';

export const routing = defineRouting({
  locales,
  defaultLocale,
  localePrefix: 'always',
});

/** Dùng các hàm này thay cho next/link, next/navigation để giữ prefix locale khi chuyển trang. */
export const { Link, getPathname, redirect, usePathname, useRouter } = createNavigation(routing);
