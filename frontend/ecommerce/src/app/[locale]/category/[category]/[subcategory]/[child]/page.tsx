import type { Locale } from '@/lib/locales';
import { redirectToCategory } from '../../redirect';

export default async function ChildCategoryPage({ params }: { params: Promise<{ locale: Locale; child: string }> }) {
  const { locale, child } = await params;
  redirectToCategory(locale, child);
}
