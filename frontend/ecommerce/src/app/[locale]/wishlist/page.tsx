import { setRequestLocale } from 'next-intl/server';
import WishlistView from '@/features/wishlist/WishlistView';
import type { Locale } from '@/lib/locales';

export default async function WishlistPage({ params }: { params: Promise<{ locale: Locale }> }) {
  setRequestLocale((await params).locale);
  return <WishlistView />;
}
