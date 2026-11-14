'use client';

import Image from 'next/image';
import { Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { toast } from 'react-toastify';
import EmptyWishlistImage from '@/assets/images/empty_wishlist.png';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import { Button, Card, Skeleton } from '@/components/ui';
import { useAddProductToCart } from '@/features/cart/hooks';
import { Link } from '@/i18n/routing';
import { Product } from '@/models/types';
import { IMAGE, PATH } from '@/utils/constants/path';
import { useRemoveProductFromWishlist, useWishlist } from './hooks';

function WishlistRow({ item }: { item: Product }) {
  const t = useTranslations();
  const remove = useRemoveProductFromWishlist();
  const addToCart = useAddProductToCart();
  const href = PATH.PRODUCT_PATH_URL.PRODUCT_DETAIL + item.slug;
  const outOfStock = item.stockStatus === 'OUT_OF_STOCK';

  return (
    <li className="flex flex-col gap-3 border-b py-4 sm:flex-row sm:items-center">
      <Link href={href} className="relative h-24 w-24 shrink-0 overflow-hidden rounded">
        <Image
          src={item.thumbnail?.imagePath || IMAGE.defaultImage}
          alt={item.name}
          fill
          sizes="96px"
          className="object-cover"
        />
      </Link>
      <div className="min-w-0 flex-1">
        <Link href={href} className="line-clamp-2 font-medium hover:underline" title={item.name}>
          {item.name}
        </Link>
        <div className="mt-1 text-sm text-muted-foreground">{item.price?.formatted}</div>
        <span
          className={
            outOfStock
              ? 'mt-1 inline-block rounded bg-status_inactive-background px-2 py-0.5 text-xs text-status_inactive-text'
              : 'mt-1 inline-block rounded bg-status_active-background px-2 py-0.5 text-xs text-status_active-text'
          }
        >
          {outOfStock ? t('common.out_of_stock') : t('common.in_stock')}
        </span>
      </div>
      <div className="flex items-center gap-2">
        <Button
          type="button"
          className="bg-yellow-400 text-black hover:bg-yellow-500"
          disabled={outOfStock || addToCart.isPending}
          onClick={() =>
            addToCart.mutate(
              { productId: item.id, quantity: 1 },
              { onSuccess: () => toast.success(t('cart.add_item_successfully')) }
            )
          }
        >
          {t('common.add_to_cart')}
        </Button>
        <ConfirmDialog
          title={t('wishlist.confirm_remove')}
          onConfirm={() =>
            remove.mutate(item.id, {
              onSuccess: () => toast.success(t('wishlist.remove_item_from_wishlist_successfully')),
            })
          }
        >
          <Button type="button" variant="ghost" size="icon" aria-label={t('common.remove_this_item')}>
            <Trash2 className="h-5 w-5" />
          </Button>
        </ConfirmDialog>
      </div>
    </li>
  );
}

export default function WishlistView() {
  const t = useTranslations();
  const { data, isPending } = useWishlist();

  return (
    <Card className="mx-auto my-6 w-full max-w-5xl p-4 sm:p-6">
      <h1 className="mb-2 text-xl font-semibold">{t('common.wishlist')}</h1>
      {isPending ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
      ) : !data?.product.length ? (
        <div className="flex flex-col items-center gap-4 py-10">
          <Image src={EmptyWishlistImage} alt="" width={240} height={240} />
          <p>{t('wishlist.empty')}</p>
          <Link href={PATH.HOME} className="hover:underline">
            {t('common.back_to_home')}
          </Link>
        </div>
      ) : (
        <ul>
          {data.product.map((item) => (
            <WishlistRow key={item.id} item={item} />
          ))}
        </ul>
      )}
    </Card>
  );
}
