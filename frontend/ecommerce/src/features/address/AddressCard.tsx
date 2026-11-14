'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { formatAddress } from '@/api/addressApi';
import type { ShippingAddress } from '@/graphql/types';

/** Thông tin một địa chỉ: tên, số điện thoại, địa chỉ, nhãn mặc định. */
export default function AddressCard({ address, actions }: { address: ShippingAddress; actions?: React.ReactNode }) {
  const t = useTranslations('address');
  return (
    <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
      <div className="min-w-0">
        <p>
          <span className="font-medium">{address.fullName}</span>
          <span className="mx-2 text-gray-400">|</span>
          <span>{address.phoneNumber}</span>
        </p>
        <p className="text-sm text-gray-700 break-words">{formatAddress(address)}</p>
        <p className="mt-1 flex gap-2 text-xs">
          <span className="border border-gray-300 rounded px-2 py-0.5">{t(`label_${address.label.toLowerCase()}`)}</span>
          {address.isDefault && <span className="bg-yellow-300 px-2 py-0.5 rounded">{t('default')}</span>}
        </p>
      </div>
      {actions && <div className="flex gap-2 shrink-0">{actions}</div>}
    </div>
  );
}
