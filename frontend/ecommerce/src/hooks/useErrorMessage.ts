'use client';

import { errorKey } from '@/lib/errors';
import { useTranslations } from 'next-intl';
import { useCallback } from 'react';

export function useErrorMessage() {
  const t = useTranslations('error');
  return useCallback((error: unknown) => t(errorKey(error)), [t]);
}
