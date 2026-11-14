'use client';

import { Pencil } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui';
import { editProfile } from '@/lib/auth/actions';

export default function EditProfileButton() {
  const t = useTranslations('common');
  return (
    <Button type="button" variant="outline" size="sm" onClick={() => editProfile()}>
      <Pencil className="h-4 w-4" />
      {t('edit')}
    </Button>
  );
}
