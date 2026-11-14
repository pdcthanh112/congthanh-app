'use client';

import React, { useEffect, useState } from 'react';
import Image from 'next/image';
import { useTranslations } from 'next-intl';
import { Trash2 } from 'lucide-react';
import { toast } from 'react-toastify';
import QuantitySelector from '@/components/quantity-selector';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import { Link } from '@/i18n/routing';
import type { CartLine } from '@/graphql/types';
import { useDeleteCartItem, useUpdateCartItem } from '@/features/cart/hooks';
import useDebounce from '@/hooks/useDebounce';
import { useErrorMessage } from '@/hooks/useErrorMessage';
import { IMAGE, PATH } from '@/utils/constants/path';

/** Một dòng trong trang giỏ hàng: ảnh, tên, đơn giá, số lượng (debounce 500ms), thành tiền, nút xoá. */
export default function CartLineItem({ line }: { line: CartLine }) {
  const t = useTranslations();
  const errorMessage = useErrorMessage();
  const [quantity, setQuantity] = useState(line.quantity);
  const debouncedQuantity = useDebounce(quantity, 500);
  const { mutate: updateItem } = useUpdateCartItem();
  const { mutate: deleteItem, isPending: deleting } = useDeleteCartItem();
  const name = line.product?.name ?? '';

  useEffect(() => {
    if (debouncedQuantity !== line.quantity) {
      updateItem(
        { itemId: line.id, quantity: debouncedQuantity },
        { onError: (e) => toast.error(errorMessage(e)) }
      );
    }
  }, [debouncedQuantity, line.quantity, line.id, updateItem, errorMessage]);

  const handleDelete = () =>
    deleteItem(line.id, {
      onSuccess: () => toast.success(t('cart.remove_item_from_cart_successfully')),
      onError: (e) => toast.error(errorMessage(e)),
    });

  return (
    <div className="grid grid-cols-[4rem_1fr_auto] sm:grid-cols-[6rem_1fr_auto_auto_auto] gap-3 items-center py-3">
      <span className="relative w-16 h-16 sm:w-24 sm:h-24 border border-gray-200 rounded">
        <Image src={line.product?.thumbnail || IMAGE.defaultImage} alt="" fill sizes="96px" className="object-cover rounded" />
      </span>
      <div className="min-w-0">
        {line.product?.slug ? (
          <Link
            href={`${PATH.PRODUCT_PATH_URL.PRODUCT_DETAIL}${line.product.slug}`}
            className="line-clamp-2 hover:underline"
            aria-label={t('cart.view_detail_of', { name })}
          >
            {name}
          </Link>
        ) : (
          <span className="line-clamp-2">{name}</span>
        )}
        <div className="text-sm text-gray-600">{line.unitPrice?.formatted}</div>
        {!line.purchasable && (
          <p role="alert" className="text-sm text-red-600">
            {line.issue ?? t('cart.line_issue')}
          </p>
        )}
      </div>
      <div className="col-start-2 sm:col-start-auto">
        <QuantitySelector initialValue={quantity} min={1} onChange={setQuantity} />
      </div>
      <span className="font-medium text-right hidden sm:block">{line.lineTotal?.formatted}</span>
      <ConfirmDialog title={t('cart.remove_item_confirm')} onConfirm={handleDelete}>
        <button
          type="button"
          disabled={deleting}
          className="row-start-1 col-start-3 sm:row-auto sm:col-auto justify-self-end p-2 rounded opacity-60 hover:opacity-100 hover:bg-gray-100"
          aria-label={t('common.remove_this_item')}
          title={t('common.remove_this_item')}
        >
          <Trash2 size={18} aria-hidden />
        </button>
      </ConfirmDialog>
    </div>
  );
}
