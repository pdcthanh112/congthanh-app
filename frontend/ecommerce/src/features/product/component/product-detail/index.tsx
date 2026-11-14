'use client';

import React, { useRef, useState } from 'react';
import Image from 'next/image';
import { useSession } from 'next-auth/react';
import { useTranslations } from 'next-intl';
import { ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { toast } from 'react-toastify';
import DefaultImage from '@/assets/images/default-image.jpg';
import { AddToCartIcon, HeartEmpty, HeartFull } from '@/assets/icons';
import StarRating from '@/components/common/StarRating';
import QuantitySelector from '@/components/quantity-selector/QuantitySelector';
import { Button, Popover, PopoverContent, PopoverTrigger } from '@/components/ui';
import { useAddProductToCart } from '@/features/cart/hooks';
import { useAddProductToWishlist, useRemoveProductFromWishlist, useWishlistIds } from '@/features/wishlist/hooks';
import { useErrorMessage } from '@/hooks/useErrorMessage';
import { cn } from '@/lib/utils';
import type { Product, ProductOptionValueDisplay, ProductOptions, ProductVariant } from '@/models/types';
import type { ReviewStatistic } from '@/models/types/Review';
import useAppModalStore from '@/store/useAppModal';
import { formatCurrency, formatNumber } from '@/utils/helper';

type ProductDetailProps = {
  /** product.description đã được sanitize phía server (xem app/[locale]/product/[slug]/page.tsx). */
  product: Product;
  pvid: string | null;
  reviewStatistic: ReviewStatistic;
  productOption?: ProductOptions[];
  productVariant?: ProductVariant[];
  productOptionValueGet?: ProductOptionValueDisplay[];
};

const THUMBS_PER_VIEW = 5;

export default function ProductDetail({
  product,
  pvid,
  reviewStatistic,
  productOption = [],
  productVariant = [],
  productOptionValueGet = [],
}: ProductDetailProps) {
  const { status } = useSession();
  const t = useTranslations();
  const errorMessage = useErrorMessage();
  const openModalAuth = useAppModalStore((state) => state.openModalAuth);
  const isAuthenticated = status === 'authenticated';

  const images = [product.thumbnail, ...product.image].filter((img) => img?.imagePath);
  const [currentImage, setCurrentImage] = useState(images[0]);
  const [thumbStart, setThumbStart] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const imageRef = useRef<HTMLImageElement>(null);

  // Biến thể: chọn sẵn theo ?pvid= hoặc biến thể đầu tiên (supergraph hiện chưa trả biến thể nên thường rỗng).
  const initialVariant = (pvid && productVariant.find((v) => v.id.toString() === pvid)) || productVariant[0];
  const [selectedOptions, setSelectedOptions] = useState<Record<number, string>>(
    () => (initialVariant?.options as Record<number, string> | undefined) ?? {}
  );

  const { mutate: addProductToCart, isPending: adding } = useAddProductToCart();
  const { mutate: addToWishlist } = useAddProductToWishlist();
  const { mutate: removeFromWishlist } = useRemoveProductFromWishlist();
  const inWishlist = useWishlistIds().has(product.id);

  const requireLogin = (action: () => void) => (isAuthenticated ? action() : openModalAuth());

  const handleAddProductToCart = () =>
    requireLogin(() =>
      addProductToCart(
        { productId: product.id, quantity },
        {
          onSuccess: () => toast.success(t('cart.add_item_to_cart_successfully')),
          onError: (e) => toast.error(errorMessage(e)),
        }
      )
    );

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

  const handleZoom = (event: React.MouseEvent<HTMLDivElement>) => {
    const image = imageRef.current;
    if (!image) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const { naturalHeight, naturalWidth } = image;
    const { offsetX, offsetY } = event.nativeEvent;
    image.style.width = `${naturalWidth}px`;
    image.style.height = `${naturalHeight}px`;
    image.style.maxWidth = 'unset';
    image.style.top = `${offsetY * (1 - naturalHeight / rect.height)}px`;
    image.style.left = `${offsetX * (1 - naturalWidth / rect.width)}px`;
  };

  const ratingRows = [5, 4, 3, 2, 1].map((star) => {
    const count = reviewStatistic[`reviewRating${star}Star` as keyof ReviewStatistic] as number;
    return { star, percent: reviewStatistic.totalReview ? Math.round((count / reviewStatistic.totalReview) * 100) : 0 };
  });

  const toggleOption = (optionId: number, value: string) =>
    setSelectedOptions((prev) => {
      if (prev[optionId] === value) {
        const next = { ...prev };
        delete next[optionId];
        return next;
      }
      return { ...prev, [optionId]: value };
    });

  const visibleThumbs = images.slice(thumbStart, thumbStart + THUMBS_PER_VIEW);

  return (
    <div className="w-full max-w-6xl mx-auto px-4 my-3">
      <div className="bg-white flex flex-col md:flex-row gap-5 px-3 py-2">
        <div className="w-full md:w-2/5 py-3">
          <div
            className="relative w-full pt-[100%] shadow-sm cursor-zoom-in overflow-hidden"
            onMouseLeave={() => imageRef.current?.removeAttribute('style')}
            onMouseMove={handleZoom}
          >
            <Image
              src={currentImage?.imagePath || DefaultImage}
              alt={product.name}
              width={600}
              height={600}
              priority
              ref={imageRef}
              className="absolute pointer-events-none top-0 left-0 h-full w-full bg-white object-cover"
            />
          </div>
          {images.length > 1 && (
            <div className="relative mt-4 grid grid-cols-5 gap-1">
              <button
                type="button"
                onClick={() => setThumbStart((s) => Math.max(0, s - 1))}
                disabled={thumbStart === 0}
                className="absolute left-0 top-1/2 z-10 h-9 w-5 -translate-y-1/2 bg-black/20 text-white disabled:opacity-30"
                aria-label={t('product.previous_images')}
              >
                <ChevronLeft className="w-5 h-5" aria-hidden />
              </button>
              <button
                type="button"
                onClick={() => setThumbStart((s) => Math.min(Math.max(0, images.length - THUMBS_PER_VIEW), s + 1))}
                disabled={thumbStart + THUMBS_PER_VIEW >= images.length}
                className="absolute right-0 top-1/2 z-10 h-9 w-5 -translate-y-1/2 bg-black/20 text-white disabled:opacity-30"
                aria-label={t('product.next_images')}
              >
                <ChevronRight className="w-5 h-5" aria-hidden />
              </button>
              {visibleThumbs.map((img, i) => (
                <button
                  type="button"
                  key={`${img.id}-${img.imagePath}`}
                  className={cn('relative aspect-square', img === currentImage && 'ring-2 ring-red-400')}
                  onClick={() => setCurrentImage(img)}
                  aria-label={t('product.view_image', { index: thumbStart + i + 1 })}
                  aria-pressed={img === currentImage}
                >
                  <Image src={img.imagePath} alt="" fill sizes="80px" className="object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="w-full md:w-3/5 p-3">
          <h1 className="font-medium text-2xl bg-gray-200 px-3 py-2 mb-3">{product.name}</h1>
          <div className="flex flex-wrap items-center justify-between gap-2 my-3">
            <div className="flex flex-wrap items-center gap-2">
              <Popover>
                <PopoverTrigger asChild>
                  <button type="button" className="flex items-center gap-1" aria-label={t('product.rating_breakdown')}>
                    <span>{reviewStatistic.averageRating}</span>
                    <StarRating value={reviewStatistic.averageRating} />
                    <ChevronDown size={16} aria-hidden />
                  </button>
                </PopoverTrigger>
                <PopoverContent className="w-80">
                  <div className="flex items-center gap-2">
                    <StarRating value={reviewStatistic.averageRating} />
                    <span>{t('product.out_of_5', { rating: reviewStatistic.averageRating })}</span>
                  </div>
                  <p className="text-sm mb-2">{t('product.ratings_count', { count: reviewStatistic.totalReview })}</p>
                  <ul className="space-y-1">
                    {ratingRows.map(({ star, percent }) => (
                      <li key={star} className="flex items-center gap-2 text-sm">
                        <span className="w-14 shrink-0">{t('product.stars', { count: star })}</span>
                        <span className="flex-1 h-3 bg-gray-200 rounded" aria-hidden>
                          <span className="block h-full bg-yellow-400 rounded" style={{ width: `${percent}%` }} />
                        </span>
                        <span className="w-10 text-right">{percent}%</span>
                      </li>
                    ))}
                  </ul>
                </PopoverContent>
              </Popover>
              <span className="opacity-80" aria-hidden>
                |
              </span>
              <span>
                {formatNumber(reviewStatistic.totalReview)} {t('product.rating')}
              </span>
            </div>

            <button
              type="button"
              onClick={toggleWishlist}
              aria-pressed={inWishlist}
              aria-label={inWishlist ? t('common.remove_from_wishlist') : t('common.add_to_wishlist')}
              title={inWishlist ? t('common.remove_from_wishlist') : t('common.add_to_wishlist')}
              className="text-red-600"
            >
              {inWishlist ? <HeartFull width={24} height={24} aria-hidden /> : <HeartEmpty width={24} height={24} aria-hidden />}
            </button>
          </div>

          <div className="font-semibold text-3xl text-yellow-500">{formatCurrency(10000000000000, 'vi', 'VND')}</div>

          {productOption.map((option) => {
            const values = productOptionValueGet.filter((v) => v.productOptionId === option.id);
            return (
              <fieldset className="my-3" key={option.name}>
                <legend className="mb-2">{option.name}:</legend>
                <div className="flex flex-wrap gap-2">
                  {(values.length > 0 ? values.map((v) => v.productOptionValue) : option.value).map((value) => (
                    <Button
                      key={value}
                      type="button"
                      variant="outline"
                      aria-pressed={selectedOptions[option.id] === value}
                      className={cn(selectedOptions[option.id] === value && 'bg-amber-200')}
                      onClick={() => toggleOption(option.id, value)}
                      aria-label={t('product.select_option', { name: option.name, value })}
                    >
                      {value}
                    </Button>
                  ))}
                </div>
              </fieldset>
            );
          })}

          <div className="flex flex-wrap items-center gap-4 sm:gap-10 mt-8">
            <span>{t('product.Quantity')}</span>
            <QuantitySelector initialValue={quantity} max={99} onChange={setQuantity} />
            <span>{t('common.available')}</span>
          </div>
          <div className="flex flex-wrap gap-3 mt-8">
            <Button
              className="bg-yellow-50 border-yellow-300 border-2 text-yellow-600 hover:bg-yellow-100"
              onClick={handleAddProductToCart}
              disabled={adding}
            >
              <AddToCartIcon width={28} height={28} aria-hidden />
              {t('common.add_to_cart')}
            </Button>
            <Button className="bg-yellow-400 text-white hover:bg-yellow-500">{t('product.buy_now')}</Button>
          </div>
        </div>
      </div>

      <section className="bg-white mt-6 p-5">
        <h2 className="bg-yellow-100 px-2 py-1 rounded-sm uppercase">{t('product.product_detail')}</h2>
        <dl className="grid grid-cols-4 gap-y-2 mt-3">
          <dt className="col-span-1">{t('common.Category')}</dt>
          <dd className="col-span-3">{product.category.map((item) => item.name).join(', ')}</dd>
          {product.brand && (
            <>
              <dt className="col-span-1">{t('product.brand')}</dt>
              <dd className="col-span-3">{product.brand}</dd>
            </>
          )}
        </dl>
      </section>

      {product.attribute.length > 0 && (
        <section className="bg-white mt-6 p-5">
          <h2 className="bg-yellow-100 px-2 py-1 rounded-sm uppercase">{t('product.product_attribute')}</h2>
          <table className="w-full mt-3 text-sm">
            <tbody className="divide-y">
              {product.attribute.map((item) => (
                <tr key={item.id}>
                  <th scope="row" className="text-left font-normal py-2 pr-4 w-1/3">
                    {item.attribute}
                  </th>
                  <td className="py-2">{item.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <section className="bg-white mt-6 p-5">
        <h2 className="bg-yellow-100 px-2 py-1 rounded-sm uppercase">{t('product.product_description')}</h2>
        {/* Đã sanitize phía server bằng sanitize-html (chặn script, on*, javascript: URL...). */}
        <div dangerouslySetInnerHTML={{ __html: product.description }} className="prose max-w-none mt-3" />
      </section>
    </div>
  );
}
