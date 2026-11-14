'use client';

import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'react-toastify';
import { cancelOrder } from '@/api/orderApi';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  Label,
} from '@/components/ui';
import { useRouter } from '@/i18n/routing';

const REASONS = ['changed_mind', 'better_price', 'wrong_info', 'delivery_too_long', 'other'] as const;

export default function CancelOrderDialog({ orderCode }: { orderCode: string }) {
  const t = useTranslations('order.cancel');
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<(typeof REASONS)[number]>('changed_mind');
  const [note, setNote] = useState('');

  const mutation = useMutation({
    mutationFn: () => cancelOrder(orderCode, [t(`reasons.${reason}`), note.trim()].filter(Boolean).join(': ')),
    onSuccess: () => {
      toast.success(t('success'));
      setOpen(false);
      router.refresh();
    },
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button type="button" variant="outline">
          {t('button')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <div>
            <Label htmlFor="cancel-reason" className="mb-1 block">
              {t('reason')}
            </Label>
            <select
              id="cancel-reason"
              className="w-full rounded-md border bg-transparent px-3 py-2"
              value={reason}
              onChange={(e) => setReason(e.target.value as (typeof REASONS)[number])}
            >
              {REASONS.map((r) => (
                <option key={r} value={r}>
                  {t(`reasons.${r}`)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <Label htmlFor="cancel-note" className="mb-1 block">
              {t('note')}
            </Label>
            <textarea
              id="cancel-note"
              rows={3}
              maxLength={500}
              className="w-full rounded-md border bg-transparent px-3 py-2"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            {t('keep')}
          </Button>
          <Button type="button" variant="destructive" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            {t('confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
