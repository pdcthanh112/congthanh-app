'use client';

import React from 'react';
import { Cart, CartItem } from '@/models/types';
import { Card, Checkbox } from '@mui/material';
import { Popconfirm } from 'antd';
import { useDeleteCart } from '@/features/cart/hooks';
import { toast } from 'react-toastify';
import { useTranslations } from 'next-intl';
import { Button, Separator } from '@/components/ui';
import { useRouter } from 'next/navigation';
import ShowCartItem from './ShowCartItem';
import { Trash2 } from 'lucide-react';
import useSelectedCheckout from '@/store/useSelectedCheckout';

type PropsType = {
  data: Cart[];
  loading: boolean;
};

export default function ShowListCart({ data }: PropsType) {
  const router = useRouter();
  const t = useTranslations();

  const { toggleAllCarts, isAllCartsSelected, selectedCarts, toggleCart, toggleCartItem, isCartSelected } =
    useSelectedCheckout();
  const { mutate: deleteCart } = useDeleteCart();

  const handleDeleteCart = (cartId: number) => {
    deleteCart(cartId, {
      onSuccess() {
        toast.success(t('cart.delete_cart_successfully'));
      },
      onError(error) {
        toast.error(t('cart.delete_cart_failed'));
        console.log(error);
      },
    });
  };

  const handleCheckout = (cartId: number) => {
    const cartSelections = selectedCarts[cartId] || {};
    const selectedItems = Object.entries(cartSelections)
      .filter(([_, isSelected]) => isSelected)
      .map(([itemId]) => Number(itemId));

    if (selectedItems.length === 0) {
      toast.error('Vui lòng chọn sản phẩm');
      return;
    }
    router.push(`/checkout?items=${encodeURIComponent(JSON.stringify(selectedItems))}`);
  };

  const handleCheckoutAll = () => {
    const selectedItems = Object.entries(selectedCarts)
      .map(([cartId, items]) => ({
        cartId: Number(cartId),
        items: Object.entries(items)
          .filter(([_, isSelected]) => isSelected)
          .map(([itemId]) => Number(itemId)),
      }))
      .filter((cart) => cart.items.length > 0);
    if (selectedItems.length === 0) {
      toast.error('Vui lòng chọn sản phẩm');
      return;
    }
    router.push('/checkout');
  };

  let totalCountItem: number = 0;
  let totalSumPrice: number = 0;

  return (
    <React.Fragment>
      <Card className="bg-yellow-100 flex items-center mb-5">
        <Checkbox checked={isAllCartsSelected(data)} onChange={() => toggleAllCarts(data)} />
        <span>Select all</span>
      </Card>
      {data.map((cart: Cart) => {
        let countItem: number = 0;
        let sumPrice: number = 0;
        return (
          <div key={cart.id} className="md:flex mb-5 ">
            <Card className="md:w-[80%] min-h-[10vh] px-4 py-5">
              <div className="flex justify-between">
                <div className="font-medium text-lg">
                  {cart.cartItems.length > 0 && (
                    <Checkbox
                      checked={isCartSelected(cart.id, cart.cartItems)}
                      onChange={() => toggleCart(cart.id, cart.cartItems)}
                    />
                  )}{' '}
                  {cart.name}
                </div>
                <Popconfirm
                  title="Are you sure to delete this cart?"
                  okText={t('common.yes')}
                  okButtonProps={{ style: { backgroundColor: '#1677ff' } }}
                  cancelText={t('common.no')}
                  onConfirm={() => {
                    handleDeleteCart(cart.id);
                  }}
                  placement="topRight"
                >
                  <Trash2 className="hover:cursor-pointer" size={20} />
                </Popconfirm>
              </div>
              {cart.cartItems?.length > 0 ? (
                <>
                  {cart.cartItems?.map((item: CartItem) => {
                    countItem++;
                    totalCountItem++;
                    sumPrice = sumPrice * 2;
                    totalSumPrice = totalSumPrice + 2;
                    // sumPrice += item.product.price * item.quantity;
                    return (
                      <div key={item.id} className="flex items-center hover:bg-gray-100 px-5 py-2">
                        <Checkbox
                          className="mr-2"
                          checked={selectedCarts[cart.id]?.[item.id] || false}
                          onChange={() => toggleCartItem(cart.id, item.id)}
                        />{' '}
                        <ShowCartItem cartId={cart.id} item={item} />
                      </div>
                    );
                  })}
                </>
              ) : (
                <div className="flex justify-center">{t('cart.this_cart_have_no_item')}</div>
              )}
            </Card>
            <Card className="md:w-[20%] ml-4 p-4">
              <span className="font-semibold text-xl">{t('common.checkout')}</span>
              {cart.cartItems?.length > 0 && (
                <div className=" flex justify-between flex-col h-[90%]">
                  <div className="px-3">
                    <div className="flex justify-between">
                      <span>Items({countItem}):</span>
                      <span>{sumPrice.toFixed(2)}</span>
                    </div>
                    <div className="flex justify-between">
                      {t('common.shipping')}: <span>{t('common.free')}</span>
                    </div>
                  </div>
                  <div>
                    <Separator className="h-[1.5px] bg-gray-400" />
                    <div className="flex justify-between px-3 w-[95%]">
                      <span>{t('cart.subtotal')}:</span>
                      <span>{sumPrice.toFixed(2)}</span>
                    </div>
                    <Button className="w-full bg-yellow-400 mt-2" onClick={() => handleCheckout(cart.id)}>
                      {t('common.checkout')}
                    </Button>
                  </div>
                </div>
              )}
            </Card>
          </div>
        );
      })}
      <div className="flex justify-end">
        <Card className="w-[22rem] px-4 py-5">
          <h3 className="font-semibold text-xl text-center mb-3">{t('common.checkout')} all product</h3>

          <div className="px-3">
            <div className="flex justify-between">
              <span>Items({totalCountItem}):</span>
              <span>{totalSumPrice.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              {t('common.shipping')}: <span>{t('common.free')}</span>
            </div>
          </div>
          <Separator className="h-[1.5px] bg-gray-400" />
          <div className="flex justify-between px-3 bottom-16">
            <span>{t('cart.subtotal')}:</span>
            <span>{totalSumPrice.toFixed(2)}</span>
          </div>

          <Button className="w-full bg-yellow-400" onClick={() => handleCheckoutAll()}>
            {t('common.checkout')}
          </Button>
        </Card>
      </div>
    </React.Fragment>
  );
}
