'use client';

import React, { useEffect, useState } from 'react';
import Image, { type StaticImageData } from 'next/image';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import PaymentCOD from '@/assets/icons/payment-cod.png';
import PaymentVISA from '@/assets/icons/payment-visa.png';
import PaymentMasterCard from '@/assets/icons/payment-mastercard.png';
import PaymentAmex from '@/assets/icons/payment-amex.png';
import PaymentDiscover from '@/assets/icons/payment-discover.png';
import PaymentJCB from '@/assets/icons/payment-jcb.png';
import PaymentPaypal from '@/assets/icons/payment-paypal.png';
import { checkout } from '@/api/checkoutApi';
import { quotePromotion } from '@/api/voucherApi';
import { formatAddress } from '@/api/addressApi';
import { Button, Input, Label, Separator, Skeleton } from '@/components/ui';
import type { PaymentMethod, PromotionQuote, ShippingAddress } from '@/graphql/types';
import { useRouter } from '@/i18n/routing';
import { useMyAddresses } from '@/features/address/hooks';
import AddressPicker from '@/features/address/AddressPicker';
import { useMyCart } from '@/features/cart/hooks';
import { useErrorMessage } from '@/hooks/useErrorMessage';
import { IMAGE } from '@/utils/constants/path';

type MethodOption = { value: PaymentMethod; label: string; icons: { src: StaticImageData; width: number }[] };

const newKey = () => crypto.randomUUID();

export default function CheckoutPage() {
  const t = useTranslations('checkout');
  const tc = useTranslations('common');
  const tAddressRequired = useTranslations('address')('required');
  const locale = useLocale();
  const router = useRouter();
  const errorMessage = useErrorMessage();
  const { data: cart, isLoading: cartLoading } = useMyCart();
  const { data: addressData } = useMyAddresses();

  const [address, setAddress] = useState<ShippingAddress | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [method, setMethod] = useState<PaymentMethod>('CASH_ON_DELIVERY');
  const [note, setNote] = useState('');
  const [voucherCode, setVoucherCode] = useState('');
  const [voucherError, setVoucherError] = useState<string | null>(null);
  const [quote, setQuote] = useState<(PromotionQuote & { code: string }) | null>(null);
  // Một khoá cho một lần đặt hàng: bấm lại/retry dùng cùng khoá nên backend không tạo đơn trùng.
  const [idempotencyKey, setIdempotencyKey] = useState(newKey);

  // Mặc định chọn địa chỉ mặc định của khách.
  useEffect(() => {
    if (!address && addressData?.defaultAddress) setAddress(addressData.defaultAddress);
  }, [address, addressData?.defaultAddress]);

  const methods: MethodOption[] = [
    { value: 'CASH_ON_DELIVERY', label: t('cod'), icons: [{ src: PaymentCOD, width: 25 }] },
    {
      value: 'CREDIT_CARD',
      label: t('credit_card'),
      icons: [PaymentVISA, PaymentMasterCard, PaymentAmex, PaymentDiscover, PaymentJCB].map((src) => ({ src, width: 25 })),
    },
    { value: 'PAYPAL', label: t('paypal'), icons: [{ src: PaymentPaypal, width: 40 }] },
  ];

  const applyVoucher = useMutation({
    mutationFn: (code: string) => quotePromotion(code),
    onSuccess: (q, code) => {
      setQuote({ ...q, code });
      setVoucherError(null);
      toast.success(t('voucher_applied'));
    },
    onError: () => {
      setQuote(null);
      setVoucherError(t('voucher_invalid'));
    },
  });

  const placeOrder = useMutation({
    mutationFn: () =>
      checkout({
        idempotencyKey,
        paymentMethod: method,
        shippingAddressId: address?.id ?? null,
        promotionCode: quote?.code ?? null,
        note: note.trim() || null,
        returnUrl: `${window.location.origin}/${locale}/checkout/thank-you`,
      }),
    onSuccess: (result) => {
      setIdempotencyKey(newKey());
      if (result.checkoutUrl) {
        // Thanh toán online: chuyển sang cổng thanh toán, cổng sẽ quay về returnUrl.
        window.location.assign(result.checkoutUrl);
        return;
      }
      toast.success(t('success'));
      router.push({ pathname: '/checkout/thank-you', query: { order: result.orderCode } });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!address) {
      toast.error(tAddressRequired);
      return;
    }
    placeOrder.mutate();
  };

  const total = quote?.total.formatted ?? cart?.subtotal.formatted;

  return (
    <div className="bg-white py-3">
      <div className="w-full max-w-6xl mx-auto px-4 flex flex-col lg:flex-row gap-4 items-start">
        <form onSubmit={submit} className="border border-gray-300 rounded-md w-full lg:flex-1 px-4 py-5 space-y-5" noValidate>
          <h1 className="font-medium text-xl">{t('title')}</h1>

          <section aria-labelledby="checkout-address">
            <h2 id="checkout-address" className="font-medium mb-2">
              {t('shipping_address')}
            </h2>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <p className="break-words">
                {address ? (
                  <>
                    <span className="font-medium">{address.fullName}</span> · {address.phoneNumber}
                    <br />
                    {formatAddress(address)}
                  </>
                ) : (
                  <span className="text-red-600">{tAddressRequired}</span>
                )}
              </p>
              <Button type="button" variant="outline" onClick={() => setPickerOpen(true)}>
                {tc('change')}
              </Button>
            </div>
          </section>

          <AddressPicker open={pickerOpen} onOpenChange={setPickerOpen} selectedId={address?.id} onSelect={setAddress} />

          <fieldset className="grid grid-cols-1 md:grid-cols-12 gap-4">
            <legend className="font-medium mb-2">{t('payment_method')}</legend>
            <div className="md:col-span-5 flex flex-col gap-2">
              {methods.map((m) => (
                <label key={m.value} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    name="payment-method"
                    value={m.value}
                    checked={method === m.value}
                    onChange={() => setMethod(m.value)}
                  />
                  <span>{m.label}</span>
                  <span className="hidden md:flex gap-2">
                    {m.icons.map((icon, i) => (
                      <Image key={i} src={icon.src} alt="" width={icon.width} />
                    ))}
                  </span>
                </label>
              ))}
            </div>

            {method !== 'CASH_ON_DELIVERY' && (
              <p className="md:col-span-7 text-sm text-muted-foreground">{t('gateway_redirect_notice')}</p>
            )}
          </fieldset>

          <div>
            <Label htmlFor="checkout-note">{t('note')}</Label>
            <textarea
              id="checkout-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={500}
              rows={3}
              className="mt-1 w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          </div>

          <div className="flex justify-center">
            <Button
              type="submit"
              className="bg-yellow-300 text-black hover:bg-yellow-400 rounded-xl w-full sm:w-2/5"
              disabled={placeOrder.isPending || !cart?.checkoutReady}
            >
              {placeOrder.isPending ? t('placing') : t('place_order')}
            </Button>
          </div>
        </form>

        <aside className="border border-gray-300 rounded-md w-full lg:w-80 px-3 pt-2 pb-5">
          <h2 className="font-medium text-lg mb-2">{t('order_detail')}</h2>
          {cartLoading && <Skeleton className="h-24 w-full" />}
          <ul className="divide-y mb-3">
            {cart?.lines.map((line) => (
              <li key={line.id} className="flex items-center gap-2 py-2">
                <Image
                  src={line.product?.thumbnail || IMAGE.defaultImage}
                  alt=""
                  width={48}
                  height={48}
                  className="border border-gray-200 object-cover"
                />
                <span className="flex-1 min-w-0 text-sm line-clamp-2">{line.product?.name}</span>
                <span className="text-sm shrink-0">
                  {line.quantity} × {line.unitPrice?.formatted}
                </span>
              </li>
            ))}
          </ul>
          <dl className="space-y-1">
            <div className="flex justify-between">
              <dt>{tc('shipping')}</dt>
              <dd>{tc('free')}</dd>
            </div>
            <div className="flex justify-between">
              <dt>{tc('tax')}</dt>
              <dd>0</dd>
            </div>
            {quote?.discount && (
              <div className="flex justify-between text-green-700">
                <dt>{t('discount')}</dt>
                <dd>-{quote.discount.formatted}</dd>
              </div>
            )}
          </dl>
          <form
            className="flex gap-2 mt-3"
            onSubmit={(e) => {
              e.preventDefault();
              if (voucherCode.trim()) applyVoucher.mutate(voucherCode.trim());
            }}
          >
            <Label htmlFor="voucher" className="sr-only">
              {t('voucher')}
            </Label>
            <Input
              id="voucher"
              value={voucherCode}
              onChange={(e) => setVoucherCode(e.target.value)}
              placeholder={t('voucher_placeholder')}
              aria-invalid={voucherError ? true : undefined}
              aria-describedby={voucherError ? 'voucher-error' : undefined}
            />
            <Button type="submit" variant="outline" disabled={applyVoucher.isPending}>
              {tc('apply')}
            </Button>
          </form>
          {voucherError && (
            <p id="voucher-error" role="alert" className="text-sm text-red-600 mt-1">
              {voucherError}
            </p>
          )}
          <Separator className="my-3" />
          <div className="flex justify-between font-medium text-lg">
            <span className="text-yellow-600">{t('order_total')}</span>
            <span>{total}</span>
          </div>
        </aside>
      </div>
    </div>
  );
}
