'use client';

import React, { useEffect, useState } from 'react';
import Image from 'next/image';
import { CartItem, CartItemDetail } from '@/models/types';
import { useQuery } from '@tanstack/react-query';
import { IMAGE } from '@/utils/constants/path';
import QuantitySelector from '@/components/quantity-selector';
import { formatCurrency } from '@/utils/helper';
import { getItemDetail } from '@/api/cartApi';
import Link from 'next/link';
import { Icon } from '@mui/material';
import { Delete } from '@mui/icons-material';
import { useTranslations } from 'next-intl';
import { useDeleteCartItem, useUpdateCartItem } from '@/features/cart/hooks';
import { Popconfirm } from 'antd';
import useDebounce from '@/hooks/useDebounce';

type PropsType = {
  cartId: number;
  item: CartItem;
};

export default function ShowCartItem({ cartId, item }: PropsType) {
  const [quantity, setQuantity] = useState(item.quantity);

  const t = useTranslations();

  const { mutate: handleUpdateCartItem } = useUpdateCartItem();

  const { mutate: handleDeleteCartItem } = useDeleteCartItem();

  const debouncedQuantity = useDebounce(quantity, 500);

  const {
    data: itemDetail,
    isLoading,
    error,
  } = useQuery<CartItemDetail | null>({
    queryKey: ['cart-item-detail', item.id],
    queryFn: async () => await getItemDetail({ cartId: cartId, itemId: item.id }).then((response) => response.data),
  });

  useEffect(() => {
    if (debouncedQuantity !== item.quantity) {
      handleUpdateCartItem({ itemId: item.id, quantity: debouncedQuantity });
    }
  }, [debouncedQuantity, item.quantity, item.id, handleUpdateCartItem]);

  const handleChange = (value: number) => {
    setQuantity(value);
  };

  if (isLoading) return <div>Loading...</div>;

  if (error) return <div>Error: {error.message}</div>;

  if (!itemDetail) return;

  return (
    <div className="justify-between items-center w-full grid grid-cols-12 gap-3" title={itemDetail.product.name}>
      <span className="w-24 h-24 relative col-span-2">
        <Image
          src={itemDetail.product.image || IMAGE.defaultImage}
          alt={itemDetail.product.name}
          objectFit="fit"
          fill
          className="border border-gray-300"
        />
      </span>
      <Link
        href={itemDetail.product.slug}
        className="truncate col-span-5"
        aria-label={`View details for ${itemDetail.product.name}`}
      >
        {itemDetail.product.name}
      </Link>
      <span className="col-span-2">{formatCurrency(120000)}</span>
      <div className="col-span-2">
        <QuantitySelector initialValue={item.quantity} min={1} onChange={(value) => handleChange(value)} />
      </div>
      <span className="col-span-1 flex justify-end">
        <Popconfirm
          title="Delete the task"
          description="Are you sure to delete this task?"
          onConfirm={() => handleDeleteCartItem(itemDetail.id)}
          // onCancel={() => {}}
          okText="Yes"
          cancelText="No"
        >
          <Icon
            titleAccess={t('common.remove_this_item')}
            component={Delete}
            className="hover:cursor-pointer opacity-50 hover:opacity-100"
            aria-label={t('common.remove_this_item')}
          />
        </Popconfirm>
      </span>
    </div>
  );
}
