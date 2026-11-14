import { setRequestLocale } from 'next-intl/server';
import { redirect } from '@/i18n/routing';
import type { Locale } from '@/lib/locales';

/** /{locale} → /{locale}/home (redirect tạm thời, không cache vĩnh viễn như 301). */
export default async function LocaleIndex({ params }: { params: Promise<{ locale: Locale }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  redirect({ href: '/home', locale });
}
