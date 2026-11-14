'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Plus } from 'lucide-react';
import { toast } from 'react-toastify';
import { Button, Dialog, DialogContent, DialogHeader, DialogTitle, Skeleton } from '@/components/ui';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import type { ShippingAddress } from '@/graphql/types';
import AddressCard from '@/features/address/AddressCard';
import AddressForm from '@/features/address/AddressForm';
import {
  useCreateAddress,
  useDeleteAddress,
  useMyAddresses,
  useSetDefaultAddress,
  useUpdateAddress,
} from '@/features/address/hooks';
import { useErrorMessage } from '@/hooks/useErrorMessage';

type Editing = { kind: 'create' } | { kind: 'edit'; address: ShippingAddress } | null;

export default function AddressPage() {
  const t = useTranslations('address');
  const errorMessage = useErrorMessage();
  const { data, isLoading } = useMyAddresses();
  const [editing, setEditing] = useState<Editing>(null);
  const create = useCreateAddress();
  const update = useUpdateAddress();
  const remove = useDeleteAddress();
  const setDefault = useSetDefaultAddress();

  const onError = (e: unknown) => toast.error(errorMessage(e));
  const onSaved = () => {
    toast.success(t('saved'));
    setEditing(null);
  };

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <h1 className="text-xl">{t('title')}</h1>
        <Button onClick={() => setEditing({ kind: 'create' })}>
          <Plus size={16} aria-hidden />
          {t('add')}
        </Button>
      </div>

      {isLoading && (
        <div className="space-y-3">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-20 w-full" />
          ))}
        </div>
      )}

      {!isLoading && (data?.addresses.length ?? 0) === 0 && <p className="py-6">{t('empty')}</p>}

      <ul className="divide-y">
        {data?.addresses.map((address) => (
          <li key={address.id} className="py-3">
            <AddressCard
              address={address}
              actions={
                <>
                  <Button variant="link" size="sm" onClick={() => setEditing({ kind: 'edit', address })}>
                    {t('edit')}
                  </Button>
                  {!address.isDefault && (
                    <>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={setDefault.isPending}
                        onClick={() => setDefault.mutate(address.id, { onError })}
                      >
                        {t('set_default')}
                      </Button>
                      <ConfirmDialog
                        title={t('delete_confirm')}
                        onConfirm={() =>
                          remove.mutate(address.id, { onSuccess: () => toast.success(t('deleted')), onError })
                        }
                      >
                        <Button variant="ghost" size="sm" className="text-red-600">
                          ✕<span className="sr-only">{t('delete_confirm')}</span>
                        </Button>
                      </ConfirmDialog>
                    </>
                  )}
                </>
              }
            />
          </li>
        ))}
      </ul>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="w-[calc(100%-2rem)] max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing?.kind === 'edit' ? t('edit') : t('add')}</DialogTitle>
          </DialogHeader>
          {editing && (
            <AddressForm
              initial={editing.kind === 'edit' ? editing.address : undefined}
              submitting={create.isPending || update.isPending}
              onCancel={() => setEditing(null)}
              onSubmit={(input, makeDefault) =>
                editing.kind === 'edit'
                  ? update.mutate({ id: editing.address.id, input }, { onSuccess: onSaved, onError })
                  : create.mutate({ input, makeDefault }, { onSuccess: onSaved, onError })
              }
            />
          )}
        </DialogContent>
      </Dialog>
    </section>
  );
}
