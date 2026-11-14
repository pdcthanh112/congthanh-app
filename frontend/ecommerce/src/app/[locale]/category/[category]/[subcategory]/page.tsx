import type { Locale } from '@/lib/locales';
import { redirectToCategory } from '../redirect';

export default async function SubcategoryPage({
  params,
}: {
  params: Promise<{ locale: Locale; subcategory: string }>;
}) {
  const { locale, subcategory } = await params;
  redirectToCategory(locale, subcategory);
}
