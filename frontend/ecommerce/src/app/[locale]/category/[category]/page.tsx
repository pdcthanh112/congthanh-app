import type { Locale } from '@/lib/locales';
import { redirectToCategory } from './redirect';

export default async function CategoryPage({ params }: { params: Promise<{ locale: Locale; category: string }> }) {
  const { locale, category } = await params;
  redirectToCategory(locale, category);
}
