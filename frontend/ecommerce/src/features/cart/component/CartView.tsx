'use client';

import React from 'react';
import Image from 'next/image';
import { useSession } from 'next-auth/react';
import { useTranslations } from 'next-intl';
import { toast } from 'react-toastify';
import { Button, Separator, Skeleton } from '@/components/ui';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import { Link } from '@/i18n/routing';
import { useClearCart, useMyCart } from '@/features/cart/hooks';
import CartLineItem from '@/features/cart/component/CartLineItem';
import { useErrorMessage } from '@/hooks/useErrorMessage';
import { login } from '@/lib/auth/actions';
import { IMAGE, PATH } from '@/utils/constants/path';

/** Nội dung trang giỏ hàng: danh sách dòng và tóm tắt thanh toán (dữ liệu thật từ MyCart). */
export default function CartView() {
  const t = useTranslations();
  const errorMessage = useErrorMessage();
  const { status } = useSession();
  const { data: cart, isLoading, isError, error } = useMyCart();
  const { mutate: clear } = useClearCart();

  if (status === 'unauthenticated') {
    return (
      <div className="p-4 text-center bg-yellow-100 rounded-md">
        <h2 className="text-lg font-medium">{t('cart.please_signin_to_view_your_cart')}</h2>
        <Button variant="link" onClick={() => login()}>
          {t('cart.login_to_view')}
        </Button>
      </div>
    );
  }

  if (status === 'loading' || isLoading) {
    return (
      <div className="space-y-3 bg-white p-4 rounded">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-24 w-full" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <p role="alert" className="p-4 bg-white rounded text-red-600">
        {errorMessage(error)}
      </p>
    );
  }

  if (!cart || cart.lines.length === 0) {
    return (
      <div className="w-full py-12 bg-white text-center rounded">
        <Image src={IMAGE.cartEmpty} alt="" width={80} height={80} className="mx-auto" />
        <p className="font-medium text-xl my-5">{t('cart.cart_empty')}</p>
        <Button asChild className="bg-green-500 rounded-xl text-white">
          <Link href={PATH.HOME}>{t('common.back_to_home')}</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col lg:flex-row gap-4 items-start">
      <section className="w-full lg:flex-1 bg-white rounded px-4 py-2 divide-y">
        {cart.lines.map((line) => (
          <CartLineItem key={line.id} line={line} />
        ))}
        <div className="flex justify-end py-3">
          <ConfirmDialog
            title={t('cart.clear_cart_confirm')}
            onConfirm={() => clear(undefined, { onError: (e) => toast.error(errorMessage(e)) })}
          >
            <Button variant="outline" size="sm">
              {t('cart.clear_cart')}
            </Button>
          </ConfirmDialog>
        </div>
      </section>
      <aside className="w-full lg:w-80 bg-white rounded p-4 lg:sticky lg:top-4">
        <h2 className="font-semibold text-xl mb-3">{t('common.checkout')}</h2>
        <div className="flex justify-between">
          <span>{t('cart.items_count', { count: cart.itemCount })}</span>
          <span>{cart.subtotal.formatted}</span>
        </div>
        <div className="flex justify-between">
          <span>{t('common.shipping')}</span>
          <span>{t('common.free')}</span>
        </div>
        <Separator className="my-3" />
        <div className="flex justify-between font-medium">
          <span>{t('cart.subtotal')}</span>
          <span>{cart.subtotal.formatted}</span>
        </div>
        {!cart.checkoutReady && (
          <p role="alert" className="mt-2 text-sm text-red-600">
            {t('cart.not_ready')}
          </p>
        )}
        <Button asChild={cart.checkoutReady} disabled={!cart.checkoutReady} className="w-full bg-yellow-400 text-black hover:bg-yellow-500 mt-3">
          {cart.checkoutReady ? <Link href="/checkout">{t('common.checkout')}</Link> : <span>{t('common.checkout')}</span>}
        </Button>
      </aside>
    </div>
  );
}
