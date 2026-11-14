'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui';

type Props = {
  title: string;
  description?: string;
  onConfirm: () => void;
  /** Phần tử bấm để mở hộp xác nhận (nên là <button>). */
  children: React.ReactElement;
};

/** Hộp xác nhận trước thao tác xoá (thay cho Popconfirm của antd). */
export default function ConfirmDialog({ title, description, onConfirm, children }: Props) {
  const [open, setOpen] = useState(false);
  const t = useTranslations('common');

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('no')}
          </Button>
          <Button
            variant="destructive"
            onClick={() => {
              setOpen(false);
              onConfirm();
            }}
          >
            {t('yes')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
