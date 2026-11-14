import { redirect } from '@/i18n/routing';
import type { Locale } from '@/lib/locales';

/** Trang danh mục dùng chung trang /product với bộ lọc category (lọc theo cấp sâu nhất trong URL). */
export function redirectToCategory(locale: Locale, slug: string): never {
  redirect({ href: { pathname: '/product', query: { category: slug } }, locale });
  throw new Error('unreachable');
}
