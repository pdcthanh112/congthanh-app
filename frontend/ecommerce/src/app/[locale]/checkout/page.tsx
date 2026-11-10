'use client';

import React, { useState } from 'react';
import { useAppSelector } from '@/redux/store';
import { useForm, SubmitHandler } from 'react-hook-form';
import { Card, FormControl, FormControlLabel, FormLabel, Radio, RadioGroup } from '@mui/material';
import { PatternFormat } from 'react-number-format';
import Image from 'next/image';
import PaymentCOD from '@/assets/icons/payment-cod.png';
import PaymentVISA from '@/assets/icons/payment-visa.png';
import PaymentMasterCard from '@/assets/icons/payment-mastercard.png';
import PaymentAmex from '@/assets/icons/payment-amex.png';
import PaymentDiscover from '@/assets/icons/payment-discover.png';
import PaymentJCB from '@/assets/icons/payment-jcb.png';
import PaymentPaypal from '@/assets/icons/payment-paypal.png';
import { getDefaultAddressOfCustomer } from '@/api/addressApi';
import { getVoucherByCode } from '@/api/voucherApi';
import SelectAddress from '@/components/Address/SelectAddress';
import { checkout } from '@/api/checkoutApi';
import { useRouter } from 'next/navigation';
import { toast } from 'react-toastify';
import { Address, Cart, CheckoutForm, Voucher } from '@/models/types';
import useSelectedCheckout from '@/store/useSelectedCheckout';
import { Button, Form, FormField, FormItem, Input } from '@/components/ui';
import { IMAGE } from '@/utils/constants/path';
import { formatCurrency } from '@/utils/helper';
import { useQuery } from '@tanstack/react-query';

export default function CheckoutPage() {
  const carts = useAppSelector((state) => state.cart);

  const { getAllSelectedItems: checkoutItem } = useSelectedCheckout();

  const filteredCarts = carts.data
    .map((cart) => {
      const selectedCart = checkoutItem().find((sc) => sc.cartId === cart.id);
      if (!selectedCart) return null;

      const filteredItems = cart.cartItems.filter((item) => selectedCart.items.includes(item.id));
      if (filteredItems.length === 0) return null;

      return {
        ...cart,
        cartItems: filteredItems,
      };
    })
    .filter(Boolean);
  console.log('FFFFFFFFFFFFFFFFFFFF', filteredCarts);
  const router = useRouter();

  const [pickPaymentMethod, setPickPaymentMethod] = useState('COD');
  const [openModalAddress, setOpenModalAddress] = useState(false);
  const [voucher, setVoucher] = useState<Voucher>();
  const [total, setTotal] = useState<number>(0);

  // useEffect(() => {
  //   if (!isLoading) {
  //     const total = cart.cartItems
  //       .reduce((accumulator: number, item: any) => {
  //         return accumulator + item.product.price * item.quantity;
  //       }, 0)
  //       .toFixed(2);
  //     setTotal(total);
  //   }
  // }, [isLoading]);

  const checkoutForm = useForm<CheckoutForm>();
  const onSubmit: SubmitHandler<CheckoutForm> = async (data) => {
    data.customer = currentUser.userInfo.accountId;
    data.cart = cartId?.toString() ?? '';
    if (!defaultAddress) {
      setError('address', { message: 'choose address' });
    } else {
      data.address = defaultAddress.id;
    }
    data.phone = currentUser.userInfo.phone;
    data.total = total;
    data.voucher = voucher?.id;
    data.payment = pickPaymentMethod;
    try {
      await checkout(data).then((response) => {
        router.push(`/checkout/${cartId}/thank-you`);
      });
    } catch (error) {
      toast.error('error');
    }
  };

  const formApplyVoucher = useForm<{ voucherCode: string }>();
  const onApplyVoucher: SubmitHandler<{ voucherCode: string }> = async (data) => {
    try {
      await getVoucherByCode(data.voucherCode).then((response) => {
        if (response && response.data) {
          setVoucher(response.data);
          setTotal((total * (100 - 30)) / 100);
        }
      });
    } catch (error) {
      formApplyVoucher.setError('voucherCode', { message: 'Code not found' + error });
    }
  };

  const { data: defaultAddress } = useQuery<Address | null>({
    queryKey: ['address'],
    queryFn: async () =>
      await getDefaultAddressOfCustomer('e6169aa0-d4a5-4740-b510-9285bbb73a0e').then((response) => response.data),
  });

  // console.log('wwwwwwwww', defaultAddress);
  // const total = cart.cartItems
  //   .reduce((accumulator: number, item: any) => {
  //     return accumulator + item.product.price * item.quantity;
  //   }, 0)
  //   .toFixed(2);

  // if (isLoading) return <div>Loading</div>;

  return (
    <div className="bg-white py-3">
      <div className="w-[80%] mx-auto flex justify-between">
        <div className="border border-gray-400 rounded-md w-[70%] px-4 py-5">
          <h4 className="font-medium text-xl my-3">Checkout information</h4>

          {/* {filteredCarts &&
            filteredCarts.map((cart) => (
              <div key={cart?.id}>
                <div>{cart?.name}</div>
                {cart?.cartItems.map((item) => (
                  <div key={item.id}>
                    <span className="w-24 h-24 relative col-span-2">
                      <Image
                        src={
                          'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT-Zqj3z-Kbm0gGW_mxc5vTu8IZ9X5WAv4q0w&s'
                        }
                        alt={item.product.name}
                        objectFit="fit"
                        fill
                        className="border border-gray-300"
                      />
                    </span>
                    <span>Product này nè</span>
                    <span>Quantity: {item.quantity}</span>
                    <span className="col-span-2">{formatCurrency(120000)}</span>
                  </div>
                ))}
              </div>
            ))} */}
          <Form {...checkoutForm}>
            <form onSubmit={checkoutForm.handleSubmit(onSubmit)}>
              <FormField
                control={checkoutForm.control}
                name="address"
                render={({ field }) => (
                  <FormItem>
                    <div className="flex justify-between">
                      {defaultAddress && (
                        <p className="truncate">
                          {defaultAddress.street}, {defaultAddress.addressLine3}, {defaultAddress.addressLine2},{' '}
                          {defaultAddress.addressLine1}, {defaultAddress.country}
                        </p>
                      )}
                      {/* <input type="hidden" value={currentUser.userInfo.accountId} {...register('customer')} />
                  <input type="hidden" value={cartId} {...register('cart')} />
                  <input type="hidden" value={address?.id} disabled {...register('address')} /> */}
                      <Button className="text-yellow-400" onClick={() => setOpenModalAddress(true)}>
                        Change
                      </Button>
                    </div>
                  </FormItem>
                )}
              />

              <SelectAddress isOpen={openModalAddress} handleOpen={setOpenModalAddress} changeAddress={setAddress} />

              <div className="grid grid-cols-12 gap-4">
                <FormControl className="col-span-5">
                  <FormLabel id="demo-radio-buttons-group-label">Payment method</FormLabel>
                  <RadioGroup
                    aria-labelledby="demo-radio-buttons-group-label"
                    defaultValue="COD"
                    name="radio-buttons-group"
                  >
                    <FormControlLabel
                      value="COD"
                      control={<Radio />}
                      label={
                        <div className="flex items-center">
                          <span>COD&nbsp;</span>
                          <span className="hidden md:flex">
                            <Image src={PaymentCOD} alt={''} width={25} />
                          </span>
                        </div>
                      }
                      onClick={() => setPickPaymentMethod('COD')}
                    />
                    <FormControlLabel
                      value="Credit card"
                      control={<Radio />}
                      label={
                        <div className="flex items-center">
                          <span>Credit card&nbsp;</span>
                          <span className="hidden md:flex gap-2">
                            <Image src={PaymentVISA} alt={''} width={25} />
                            <Image src={PaymentMasterCard} alt={''} width={25} />
                            <Image src={PaymentAmex} alt={''} width={25} />
                            <Image src={PaymentDiscover} alt={''} width={25} />
                            <Image src={PaymentJCB} alt={''} width={25} />
                          </span>
                        </div>
                      }
                      onClick={() => setPickPaymentMethod('Credit card')}
                    />
                    <FormControlLabel
                      value="Paypal"
                      control={<Radio />}
                      label={
                        <div className="flex items-center">
                          <span>PayPal&nbsp;</span>
                          <span className="hidden md:flex">
                            <Image src={PaymentPaypal} alt={''} width={40} />
                          </span>
                        </div>
                      }
                      onClick={() => setPickPaymentMethod('Paypal')}
                    />
                  </RadioGroup>
                </FormControl>

                {pickPaymentMethod !== 'COD' && (
                  <div className="col-span-7">
                    <div className="flex justify-between">
                      <FormField
                        control={checkoutForm.control}
                        name="card_number"
                        render={({}) => (
                          <FormItem>
                            <FormLabel>Card Number</FormLabel>
                            <FormControl>
                              <PatternFormat
                                format="#### #### #### ####"
                                allowEmptyFormatting
                                mask="_"
                                className="focus:outline-hidden"
                              />
                            </FormControl>
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={checkoutForm.control}
                        name="card_number"
                        render={({}) => (
                          <FormItem>
                            <FormLabel>Expired Date</FormLabel>
                            <FormControl>
                              <PatternFormat
                                format="##/##"
                                allowEmptyFormatting
                                mask="_"
                                className="focus:outline-hidden"
                                style={{ width: 80 }}
                              />
                            </FormControl>
                          </FormItem>
                        )}
                      />
                    </div>
                    {/* <InputComponent title="Name on card" error={formState.errors.address?.message} className="">
                        <InputField>
                          <input 
                          {...register('name', {
                            required: 'Name is require',
                          })}
                            placeholder="Enter name on card"
                            className={`focus:outline-hidden ml-3 w-[100%] ${formState.errors.name && 'bg-red-100'}`}
                          />
                        </InputField>
                      </InputComponent> */}

                    <FormField
                      control={checkoutForm.control}
                      name="cvv"
                      render={({}) => (
                        <FormItem>
                          <FormLabel>CVV</FormLabel>
                          <FormControl>
                            <PatternFormat
                              format="###"
                              allowEmptyFormatting
                              mask="X"
                              className="focus:outline-hidden"
                              style={{ width: 80, opacity: 0.6 }}
                            />
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </div>
                )}
              </div>
              <div className="flex justify-center">
                <Button type="submit" className="bg-yellow-300 rounded-xl w-[40%]">
                  Checkout
                </Button>
              </div>
            </form>
          </Form>
        </div>

        <Card className="border border-gray-400 rounded-md w-[28%] h-fit px-3 pt-2 pb-5">
          <div className="border-b-2 border-b-gray-300 mb-3 pb-2">
            <h4 className="font-medium text-lg">Order detail</h4>
            <div className="flex justify-between">
              {/* <span>Items ({cart.cartItems.length}):</span>
              <span>
                $
                {cart.cartItems
                  .reduce((accumulator: number, item: any) => {
                    return accumulator + item.product.price * item.quantity;
                  }, 0)
                  .toFixed(2)}
              </span> */}
            </div>
            <div className="flex justify-between">
              <span>Shipping:</span>
              <span>Free</span>
            </div>
            <div className="flex justify-between">
              <span>Tax:</span>
              <span>0.0</span>
            </div>
            <form onSubmit={formApplyVoucher.handleSubmit(onApplyVoucher)}>
              <div className="flex">
                <FormField
                  control={checkoutForm.control}
                  name="card_number"
                  render={({}) => (
                    <FormItem>
                      <FormLabel>Card Number</FormLabel>
                      <FormControl>
                        <Input
                          type="text"
                          placeholder="Enter voucher code"
                          {...formApplyVoucher.register('voucherCode')}
                          className={`focus:outline-hidden ml-3 w-[100%] ${
                            formApplyVoucher.formState.errors.voucherCode ? 'bg-red-100' : ''
                          }`}
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <Button className="bg-yellow-300 rounded-xl ml-3 h-fit">Apply</Button>
              </div>
            </form>
          </div>
          <div className="border-b-2 border-b-gray-300 mb-3">
            <h4 className="font-medium text-lg text-yellow-500">Order total:</h4>
            <div className="flex justify-between">
              <span>Total:</span>
              <span>${total}</span>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
}
