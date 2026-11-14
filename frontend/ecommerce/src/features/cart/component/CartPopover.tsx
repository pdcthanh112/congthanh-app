'use client';

import React from 'react';
import Image from 'next/image';
import { useSession } from 'next-auth/react';
import { useTranslations } from 'next-intl';
import { ShoppingCartIcon } from '@/assets/icons';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui';
import { Link } from '@/i18n/routing';
import { useMyCart } from '@/features/cart/hooks';
import { IMAGE, PATH } from '@/utils/constants/path';

/** Biểu tượng giỏ trên header: số lượng sản phẩm và xem nhanh các dòng trong giỏ. */
export default function CartPopover() {
  const t = useTranslations();
  const { status } = useSession();
  const { data: cart } = useMyCart();
  const count = cart?.itemCount ?? 0;

  if (status !== 'authenticated') {
    return (
      <Link href={PATH.CART_PATH_URL.CART} aria-label={t('common.cart')} className="relative">
        <ShoppingCartIcon width={30} height={30} aria-hidden />
      </Link>
    );
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button type="button" className="relative" aria-label={`${t('common.cart')}: ${t('cart.items_count', { count })}`}>
          <ShoppingCartIcon width={30} height={30} aria-hidden />
          {count > 0 && (
            <span className="absolute -top-1 -right-2 min-w-4 h-4 px-1 bg-yellow-400 text-[10px] leading-4 text-center rounded-full text-black font-bold">
              {count}
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(25rem,calc(100vw-2rem))] p-0">
        <div className="max-h-80 overflow-y-auto divide-y">
          {cart && cart.lines.length > 0 ? (
            cart.lines.map((line) => (
              <Link
                key={line.id}
                href={line.product?.slug ? `${PATH.PRODUCT_PATH_URL.PRODUCT_DETAIL}${line.product.slug}` : PATH.CART_PATH_URL.CART}
                className="flex items-center justify-between gap-2 px-4 py-2 hover:bg-gray-100"
              >
                <span className="flex items-center min-w-0">
                  <Image
                    src={line.product?.thumbnail || IMAGE.defaultImage}
                    alt=""
                    width={40}
                    height={40}
                    className="border border-gray-300 object-cover"
                  />
                  <span className="truncate ml-2 text-sm">{line.product?.name}</span>
                </span>
                <span className="text-sm text-yellow-600 shrink-0">
                  {line.quantity} × {line.unitPrice?.formatted}
                </span>
              </Link>
            ))
          ) : (
            <p className="px-4 py-6 text-center">{t('cart.cart_empty')}</p>
          )}
        </div>
        <div className="flex items-center justify-between px-4 py-3 border-t">
          <span className="text-sm">
            {t('cart.items_count', { count })} · {cart?.subtotal.formatted}
          </span>
          <Link href={PATH.CART_PATH_URL.CART} className="bg-yellow-400 rounded-xl px-3 py-1 text-sm">
            {t('cart.view_cart')}
          </Link>
        </div>
      </PopoverContent>
    </Popover>
  );
}
