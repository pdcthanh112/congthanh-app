import DefaultImage from '@/assets/images/default-image.jpg';
import type { ProductCard } from '@/graphql/types';
import { Link } from '@/i18n/routing';
import { PATH } from '@/utils/constants/path';
import { formatMoney } from '@/utils/helper';
import { useLocale } from 'next-intl';
import Image from 'next/image';

export default function ChatProductCard({ product }: { product: ProductCard }) {
  const locale = useLocale();
  return (
    <Link
      href={PATH.PRODUCT_PATH_URL.PRODUCT_DETAIL + (product.slug ?? product.id)}
      className="flex w-40 shrink-0 flex-col rounded border bg-white p-2 text-xs hover:shadow"
    >
      <span className="relative mb-1 block aspect-square w-full overflow-hidden rounded">
        <Image src={product.thumbnail || DefaultImage} alt={product.name} fill sizes="160px" className="object-cover" />
      </span>
      <span className="line-clamp-2">{product.name}</span>
      <span className="mt-1 font-medium text-yellow-600">{formatMoney(product.price, locale)}</span>
    </Link>
  );
}
