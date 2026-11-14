'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { toast } from 'react-toastify';
import { Button, Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui';
import type { ShippingAddress } from '@/graphql/types';
import { useCreateAddress, useMyAddresses, useUpdateAddress } from '@/features/address/hooks';
import AddressCard from '@/features/address/AddressCard';
import AddressForm from '@/features/address/AddressForm';
import { useErrorMessage } from '@/hooks/useErrorMessage';

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  selectedId?: string | null;
  onSelect: (address: ShippingAddress) => void;
};

type Mode = { kind: 'list' } | { kind: 'create' } | { kind: 'edit'; address: ShippingAddress };

/** Hộp thoại chọn địa chỉ giao hàng ở trang checkout, có thể thêm/sửa địa chỉ tại chỗ. */
export default function AddressPicker({ open, onOpenChange, selectedId, onSelect }: Props) {
  const t = useTranslations('address');
  const errorMessage = useErrorMessage();
  const { data } = useMyAddresses();
  const [mode, setMode] = useState<Mode>({ kind: 'list' });
  const [choice, setChoice] = useState<string | null | undefined>(selectedId);
  const create = useCreateAddress();
  const update = useUpdateAddress();
  const addresses = data?.addresses ?? [];

  const close = () => {
    setMode({ kind: 'list' });
    onOpenChange(false);
  };

  const confirm = () => {
    const picked = addresses.find((a) => a.id === choice);
    if (picked) onSelect(picked);
    close();
  };

  return (
    <Dialog open={open} onOpenChange={(o) => (o ? onOpenChange(true) : close())}>
      <DialogContent className="w-[calc(100%-2rem)] max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{mode.kind === 'list' ? t('choose') : mode.kind === 'create' ? t('add') : t('edit')}</DialogTitle>
        </DialogHeader>

        {mode.kind === 'list' && (
          <>
            <fieldset className="divide-y">
              <legend className="sr-only">{t('choose')}</legend>
              {addresses.length === 0 && <p className="py-4">{t('empty')}</p>}
              {addresses.map((a) => (
                <label key={a.id} className="flex gap-3 py-3 cursor-pointer">
                  <input
                    type="radio"
                    name="shipping-address"
                    value={a.id}
                    checked={choice === a.id}
                    onChange={() => setChoice(a.id)}
                    className="mt-1"
                  />
                  <div className="flex-1">
                    <AddressCard
                      address={a}
                      actions={
                        <Button type="button" variant="link" size="sm" onClick={() => setMode({ kind: 'edit', address: a })}>
                          {t('edit')}
                        </Button>
                      }
                    />
                  </div>
                </label>
              ))}
            </fieldset>
            <DialogFooter className="flex-col sm:flex-row gap-2 sm:justify-between">
              <Button type="button" variant="outline" onClick={() => setMode({ kind: 'create' })}>
                {t('add')}
              </Button>
              <div className="flex gap-2 justify-end">
                <Button type="button" variant="outline" onClick={close}>
                  {t('cancel')}
                </Button>
                <Button type="button" onClick={confirm} disabled={!choice}>
                  {t('confirm')}
                </Button>
              </div>
            </DialogFooter>
          </>
        )}

        {mode.kind === 'create' && (
          <AddressForm
            submitting={create.isPending}
            onCancel={() => setMode({ kind: 'list' })}
            onSubmit={(input, makeDefault) =>
              create.mutate(
                { input, makeDefault },
                {
                  onSuccess: (created) => {
                    toast.success(t('saved'));
                    setChoice(created.id);
                    setMode({ kind: 'list' });
                  },
                  onError: (e) => toast.error(errorMessage(e)),
                }
              )
            }
          />
        )}

        {mode.kind === 'edit' && (
          <AddressForm
            initial={mode.address}
            submitting={update.isPending}
            onCancel={() => setMode({ kind: 'list' })}
            onSubmit={(input) =>
              update.mutate(
                { id: mode.address.id, input },
                {
                  onSuccess: () => {
                    toast.success(t('saved'));
                    setMode({ kind: 'list' });
                  },
                  onError: (e) => toast.error(errorMessage(e)),
                }
              )
            }
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
