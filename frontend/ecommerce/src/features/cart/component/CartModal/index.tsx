import React, { useState } from 'react';
import { useForm, SubmitHandler } from 'react-hook-form';
import { Card, Icon } from '@mui/material';
import { Done as DoneIcon, Clear, HighlightOff, Delete } from '@mui/icons-material';
import { Cart, CartItem, Customer } from '@/models/types';
import { useAppSelector } from '@/redux/store';
import { getCartByCustomerId } from '@/api/cartApi';
import { useQuery } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import Image from 'next/image';
import { Checkbox, Popconfirm } from 'antd';
import CartEmptyImage from '@/assets/images/cart-empty-image.png';
import { useTranslations } from 'next-intl';
import { useCreateNewCart, useDeleteCart } from '@/features/cart/hooks';
import Link from 'next/link';
import { CreateCartForm } from '@/models/form';
import ShowCartItem from './ShowCartItem';
import { ShoppingCartIcon } from '@/assets/icons';
import { useRouter } from 'next/navigation';
import { CART_KEY } from '@/utils/constants/queryKey';
import { HoverCard, HoverCardContent, HoverCardTrigger } from '@/components/ui/hover-card';

const CartModal = () => {
  const t = useTranslations();
  const router = useRouter();

  const currentUser: Customer = useAppSelector((state) => state.auth.currentUser);

  const [isCreateCart, setIsCreateCart] = useState(false);

  const { mutate: createNewCart } = useCreateNewCart();
  const { mutate: deleteCart } = useDeleteCart();

  const { data: listCart } = useQuery({
    queryKey: [CART_KEY],
    queryFn: async () => await getCartByCustomerId(currentUser.userInfo.accountId).then((response) => response.data),
  });

  const { register, resetField, handleSubmit, setValue } = useForm<CreateCartForm>();
  const onSubmit: SubmitHandler<CreateCartForm> = async (data) => {
    data.customer = currentUser.userInfo.accountId;
    createNewCart(data, {
      onSuccess: () => {
        toast.success(t('cart.create_new_cart_successfully'));
        setIsCreateCart(false);
      },
    });
  };

  const handleDeleteCart = (cartId: string) => {
    deleteCart(cartId, {
      onSuccess() {
        toast.success(t('cart.delete_cart_successfully'));
      },
      onError() {
        toast.error(t('cart.delete_cart_failed'));
      },
    });
  };

  let countItem = 0;

  // if (!currentUser)
  //   return (
  //     <div className="text-center">
  //       <Image src={CartEmptyImage} alt={'Cart empty'} width={200} className="mx-auto mt-10 mb-3" />
  //       <div className="text-lg">{t('cart.please_signin_to_view_your_cart')}</div>
  //     </div>
  //   );

  return (
    <React.Fragment>
      <HoverCard>
        <HoverCardTrigger asChild>
          <div className="flex items-start justify-center relative">
            <ShoppingCartIcon
              width={30}
              height={30}
              onClick={() => router.push('/cart')}
              className="hover:cursor-pointer"
            />
            {listCart?.length > 0 && (
              <span className=" -top-1 right-7 h-4 w-4 bg-yellow-400 text-center rounded-full text-black font-bold">
                {listCart?.length}
              </span>
            )}
          </div>
        </HoverCardTrigger>
        <HoverCardContent className="w-[25rem]">
          <Card className="hidden absolute top-8 right-0 py-2 w-[25rem] group-hover:block group-hover:z-50 max-h-96 group-hover:overflow-y-scroll">
            <div className="text-base">
              <div
                className="flex justify-end mr-5 hover:cursor-pointer hover:underline"
                onClick={() => setIsCreateCart(true)}
              >
                {t('cart.create_new_cart')}
              </div>
              {isCreateCart && (
                <form onSubmit={handleSubmit(onSubmit)}>
                  <div className="flex justify-end items-center">
                    <div className="border border-gray-400 px-2 py-1 rounded">
                      <input
                        type="text"
                        {...register('name', { value: 'New cart' })}
                        defaultValue="New cart"
                        placeholder="Enter cart name"
                        className="focus:outline-hidden w-[12rem]"
                      />
                      <Checkbox onChange={(event) => setValue('isDefault', event.target.value)}>Default</Checkbox>
                      <Icon
                        component={HighlightOff}
                        className="hover:cursor-pointer opacity-80"
                        titleAccess="Clear"
                        onClick={() => resetField('name')}
                      />
                    </div>
                    <Icon
                      component={Clear}
                      titleAccess="Cancel"
                      className="hover:cursor-pointer"
                      onClick={() => setIsCreateCart(false)}
                    />
                    <button className="hover:cursor-pointer" title="Create">
                      <Icon component={DoneIcon} />
                    </button>
                  </div>
                </form>
              )}

              {listCart?.map((cart: Cart) => (
                <div key={cart.id} className="border-b-2 border-b-gray-200">
                  <div className="flex justify-between px-2">
                    <h4 className="font-medium text-lg">{cart.name}</h4>
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
                      <Icon
                        component={Delete}
                        fontSize="small"
                        className="opacity-50 hover:opacity-80 hover:cursor-pointer"
                        titleAccess="Delete cart"
                      />
                    </Popconfirm>
                  </div>
                  {cart.cartItems?.length > 0 ? (
                    <React.Fragment>
                      {cart.cartItems.map((item: CartItem) => {
                        countItem += 1;
                        return <ShowCartItem item={item} key={item.id} />;
                      })}
                    </React.Fragment>
                  ) : (
                    <div className="flex justify-center">{t('cart.this_cart_have_no_item')}</div>
                  )}
                </div>
              ))}

              <div className="flex justify-between mx-3 my-3">
                <span>{countItem} item(s)</span>
                <Link href={'/cart'} className="bg-yellow-400 rounded-xl px-3 py-1">
                  {t('cart.view_cart')}
                </Link>
              </div>
            </div>
          </Card>
        </HoverCardContent>
      </HoverCard>
    </React.Fragment>
  );
};

export default CartModal;
