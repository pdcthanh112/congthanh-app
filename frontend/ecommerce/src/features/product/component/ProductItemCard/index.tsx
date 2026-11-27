'use client';

import DefaultImage from '@/assets/images/default-image.jpg';
import StarRating from '@/components/common/StarRating';
import { Button, Card } from '@/components/ui';
import { useAddProductToCart } from '@/features/cart/hooks';
import { useAddProductToWishlist, useRemoveProductFromWishlist, useWishlistIds } from '@/features/wishlist/hooks';
import { Link } from '@/i18n/routing';
import { cn } from '@/lib/utils';
import { Product } from '@/models/types';
import useAppModalStore from '@/store/useAppModal';
import { PATH } from '@/utils/constants/path';
import { formatMoney, formatNumber } from '@/utils/helper';
import { Heart } from 'lucide-react';
import { useSession } from 'next-auth/react';
import { useLocale, useTranslations } from 'next-intl';
import Image from 'next/image';
import { toast } from 'react-toastify';

type ProductItemProps = {
  product: Product;
  /** Ảnh ở màn hình đầu trang (LCP) thì tải ưu tiên. */
  priority?: boolean;
};

const ProductItemCard = ({ product, priority = false }: ProductItemProps) => {
  const t = useTranslations();
  const locale = useLocale();
  const { status } = useSession();
  const openModalAuth = useAppModalStore((s) => s.openModalAuth);
  const wishlistIds = useWishlistIds();
  const inWishlist = product.inWishlist || wishlistIds.has(product.id);

  const addToCart = useAddProductToCart();
  const addToWishlist = useAddProductToWishlist();
  const removeFromWishlist = useRemoveProductFromWishlist();

  const requireAuth = (action: () => void) => (status === 'authenticated' ? action() : openModalAuth());

  const handleAddToCart = () =>
    requireAuth(() =>
      addToCart.mutate(
        { productId: product.id, quantity: 1 },
        { onSuccess: () => toast.success(t('cart.add_item_successfully')) }
      )
    );

  const handleToggleWishlist = () =>
    requireAuth(() =>
      inWishlist
        ? removeFromWishlist.mutate(product.id, {
            onSuccess: () => toast.success(t('wishlist.remove_item_from_wishlist_successfully')),
          })
        : addToWishlist.mutate(product.id, {
            onSuccess: () => toast.success(t('wishlist.add_item_to_wishlist_successfully')),
          })
    );

  const rating = product.ratingSummary ?? { average: 0, count: 0 };
  const href = PATH.PRODUCT_PATH_URL.PRODUCT_DETAIL + product.slug;

  return (
    <Card className="flex flex-col p-3 text-sm">
      <Link href={href} className="relative block aspect-square w-full overflow-hidden rounded">
        <Image
          src={product.thumbnail?.imagePath || DefaultImage}
          alt={product.name}
          fill
          priority={priority}
          sizes="(min-width: 1280px) 20vw, (min-width: 1024px) 25vw, (min-width: 768px) 33vw, 50vw"
          className="object-cover"
        />
      </Link>

      <div className="flex flex-1 flex-col">
        <Link href={href} className="my-2 truncate opacity-80 hover:opacity-100" title={product.name}>
          {product.name}
        </Link>

        <div className="mb-3 flex items-baseline justify-end gap-2">
          {!!product.discountPercent && product.originalPrice && (
            <span className="text-xs text-gray-400 line-through">{formatMoney(product.originalPrice, locale)}</span>
          )}
          {product.price && (
            <span className="text-lg font-medium text-yellow-500">{formatMoney(product.price, locale)}</span>
          )}
        </div>

        <div className="mb-3 flex items-center justify-between gap-2">
          <span className="flex items-center">
            <StarRating value={rating.average} size={14} />
            <span className="ml-1">{rating.average.toFixed(1)}</span>
          </span>
          <span className="text-muted-foreground">
            {formatNumber(rating.count)} {t('product.rating')}
          </span>
        </div>

        <div className="mt-auto flex items-center gap-2">
          <Button
            type="button"
            className="flex-1 bg-yellow-400 text-black hover:bg-yellow-500"
            onClick={handleAddToCart}
            disabled={addToCart.isPending}
          >
            {t('common.add_to_cart')}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-pressed={inWishlist}
            aria-label={inWishlist ? t('common.remove_from_wishlist') : t('common.add_to_wishlist')}
            onClick={handleToggleWishlist}
            disabled={addToWishlist.isPending || removeFromWishlist.isPending}
          >
            <Heart className={cn('h-6 w-6 text-red-600', inWishlist && 'fill-red-600')} />
          </Button>
        </div>
      </div>
    </Card>
  );
};

export default ProductItemCard;
