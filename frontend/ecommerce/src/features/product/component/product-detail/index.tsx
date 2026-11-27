'use client';

import { trackProductView } from '@/api/productApi';
import { AddToCartIcon, HeartEmpty, HeartFull } from '@/assets/icons';
import QuantitySelector from '@/components/quantity-selector/QuantitySelector';
import { Button } from '@/components/ui';
import { useAddProductToCart } from '@/features/cart/hooks';
import { useAddProductToWishlist, useRemoveProductFromWishlist, useWishlistIds } from '@/features/wishlist/hooks';
import type { ProductDetail as ProductDetailData } from '@/graphql/types';
import { useErrorMessage } from '@/hooks/useErrorMessage';
import { useRouter } from '@/i18n/routing';
import useAppModalStore from '@/store/useAppModal';
import { PATH } from '@/utils/constants/path';
import { formatMoney } from '@/utils/helper';
import { useSession } from 'next-auth/react';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-toastify';
import ProductGallery from './ProductGallery';
import ProductSpecifications from './ProductSpecifications';
import RatingBreakdown from './RatingBreakdown';
import VariantPicker from './VariantPicker';
import { findVariant, type OptionSelection, selectionOf } from './variants';

const MAX_QUANTITY = 99;

interface ProductDetailProps {
  product: ProductDetailData;
  pvid: string | null;
}

export default function ProductDetail({ product, pvid }: ProductDetailProps) {
  const { status } = useSession();
  const t = useTranslations();
  const locale = useLocale();
  const router = useRouter();
  const errorMessage = useErrorMessage();
  const openModalAuth = useAppModalStore((state) => state.openModalAuth);
  const isAuthenticated = status === 'authenticated';

  const [selection, setSelection] = useState<OptionSelection>(() =>
    selectionOf(product.variants.find((v) => v.id === pvid))
  );
  const [quantity, setQuantity] = useState(1);

  const { mutate: addProductToCart, isPending: adding } = useAddProductToCart();
  const { mutate: addToWishlist } = useAddProductToWishlist();
  const { mutate: removeFromWishlist } = useRemoveProductFromWishlist();
  const inWishlist = useWishlistIds().has(product.id);

  useEffect(() => {
    trackProductView(product.id).catch(() => undefined);
  }, [product.id]);

  const variant = findVariant(product.variants, product.options.length, selection);
  const price = variant?.price ?? product.price;
  const showOriginalPrice = !variant && product.discountPercent > 0;
  const stock = product.hasVariant ? (variant?.stock ?? null) : product.stock;
  const needsSelection = product.hasVariant && !variant;
  const soldOut = stock !== null && stock.available <= 0;
  const maxQuantity = Math.max(1, Math.min(MAX_QUANTITY, stock?.available ?? MAX_QUANTITY));

  const images = useMemo(() => {
    const own = [product.thumbnail, ...product.images];
    const all = variant ? [variant.thumbnail, ...variant.images, ...own] : own;
    return [...new Set(all.filter((src): src is string => !!src))];
  }, [product.thumbnail, product.images, variant]);

  const requireLogin = (action: () => void) => (isAuthenticated ? action() : openModalAuth());

  const addToCart = (onAdded: () => void) => {
    if (needsSelection) {
      toast.warn(t('product.choose_options'));
      return;
    }
    requireLogin(() =>
      addProductToCart(
        { productId: variant?.id ?? product.id, quantity: Math.min(quantity, maxQuantity) },
        { onSuccess: onAdded, onError: (e) => toast.error(errorMessage(e)) }
      )
    );
  };

  const handleAddToCart = () => addToCart(() => toast.success(t('cart.add_item_to_cart_successfully')));
  const handleBuyNow = () => addToCart(() => router.push(PATH.CART_PATH_URL.CART));

  const toggleWishlist = () =>
    requireLogin(() =>
      inWishlist
        ? removeFromWishlist(product.id, {
            onSuccess: () => toast.success(t('wishlist.remove_item_from_wishlist_successfully')),
            onError: (e) => toast.error(errorMessage(e)),
          })
        : addToWishlist(product.id, {
            onSuccess: () => toast.success(t('wishlist.add_item_to_wishlist_successfully')),
            onError: (e) => toast.error(errorMessage(e)),
          })
    );

  const toggleOption = (optionId: string, value: string) =>
    setSelection((prev) => {
      const next = { ...prev };
      if (prev[optionId] === value) delete next[optionId];
      else next[optionId] = value;
      return next;
    });

  return (
    <div className="w-full max-w-6xl mx-auto px-4 my-3">
      <div className="bg-white flex flex-col md:flex-row gap-5 px-3 py-2">
        <ProductGallery images={images} alt={product.name} />

        <div className="w-full md:w-3/5 p-3">
          <h1 className="font-medium text-2xl bg-gray-200 px-3 py-2 mb-3">{product.name}</h1>
          <div className="flex flex-wrap items-center justify-between gap-2 my-3">
            <RatingBreakdown rating={product.ratingSummary} />
            <button
              type="button"
              onClick={toggleWishlist}
              aria-pressed={inWishlist}
              aria-label={inWishlist ? t('common.remove_from_wishlist') : t('common.add_to_wishlist')}
              title={inWishlist ? t('common.remove_from_wishlist') : t('common.add_to_wishlist')}
              className="text-red-600"
            >
              {inWishlist ? (
                <HeartFull width={24} height={24} aria-hidden />
              ) : (
                <HeartEmpty width={24} height={24} aria-hidden />
              )}
            </button>
          </div>

          <div className="flex flex-wrap items-baseline gap-3">
            <span className="font-semibold text-3xl text-yellow-500">{formatMoney(price, locale)}</span>
            {showOriginalPrice && (
              <>
                <span className="text-gray-400 line-through">{formatMoney(product.originalPrice, locale)}</span>
                <span className="text-sm text-red-500">-{product.discountPercent}%</span>
              </>
            )}
          </div>

          <VariantPicker
            options={product.options}
            variants={product.variants}
            selection={selection}
            onToggle={toggleOption}
          />

          <div className="flex flex-wrap items-center gap-4 sm:gap-10 mt-8">
            <span>{t('product.Quantity')}</span>
            <QuantitySelector initialValue={quantity} max={maxQuantity} onChange={setQuantity} />
            {stock !== null && (
              <span className={soldOut ? 'text-red-500' : undefined}>
                {soldOut ? t('common.out_of_stock') : t('product.stock_left', { count: stock.available })}
              </span>
            )}
          </div>
          <div className="flex flex-wrap gap-3 mt-8">
            <Button
              className="bg-yellow-50 border-yellow-300 border-2 text-yellow-600 hover:bg-yellow-100"
              onClick={handleAddToCart}
              disabled={adding || soldOut}
            >
              <AddToCartIcon width={28} height={28} aria-hidden />
              {t('common.add_to_cart')}
            </Button>
            <Button
              className="bg-yellow-400 text-white hover:bg-yellow-500"
              onClick={handleBuyNow}
              disabled={adding || soldOut}
            >
              {t('product.buy_now')}
            </Button>
          </div>
        </div>
      </div>

      <section className="bg-white mt-6 p-5">
        <h2 className="bg-yellow-100 px-2 py-1 rounded-sm uppercase">{t('product.product_detail')}</h2>
        <dl className="grid grid-cols-4 gap-y-2 mt-3">
          <dt className="col-span-1">{t('common.Category')}</dt>
          <dd className="col-span-3">{product.categories.map((item) => item.name).join(', ')}</dd>
          {product.brandName && (
            <>
              <dt className="col-span-1">{t('product.brand')}</dt>
              <dd className="col-span-3">{product.brandName}</dd>
            </>
          )}
          <dt className="col-span-1">{t('product.sku')}</dt>
          <dd className="col-span-3">{variant?.sku ?? product.sku}</dd>
        </dl>
      </section>

      <ProductSpecifications attributes={product.attributes} specifications={product.specifications} />

      <section className="bg-white mt-6 p-5">
        <h2 className="bg-yellow-100 px-2 py-1 rounded-sm uppercase">{t('product.product_description')}</h2>
        <div dangerouslySetInnerHTML={{ __html: product.description ?? '' }} className="prose max-w-none mt-3" />
      </section>
    </div>
  );
}
