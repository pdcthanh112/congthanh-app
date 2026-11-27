'use client';

import React, { useTransition } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { Globe } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { usePathname, useRouter } from '@/i18n/routing';
import { cn } from '@/lib/utils';
import { localeNames, locales, type Locale } from '@/lib/locales';

/** Đổi ngôn ngữ bằng cách chuyển sang cùng trang với prefix locale mới (giữ query string). */
export default function LanguageSwitcher() {
  const t = useTranslations('language');
  const locale = useLocale() as Locale;
  const router = useRouter();
  const pathname = usePathname();
  const [pending, startTransition] = useTransition();
  const [open, setOpen] = React.useState(false);

  const change = (next: Locale) => {
    setOpen(false);
    // Đọc query lúc bấm thay vì useSearchParams(): component nằm trong header của mọi trang,
    // dùng useSearchParams sẽ buộc các trang tĩnh phải render phía client.
    const query = Object.fromEntries(new URLSearchParams(window.location.search).entries());
    startTransition(() => router.replace({ pathname, query }, { locale: next }));
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <button type="button" className="flex items-center gap-1 font-semibold" aria-label={t('current', { name: localeNames[locale] })} disabled={pending}>
          <Globe size={18} aria-hidden />
          <span className="uppercase">{locale}</span>
        </button>
      </DialogTrigger>
      <DialogContent className="w-[calc(100%-2rem)] max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('choose')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>
        <ul className="grid grid-cols-2 sm:grid-cols-3 gap-2 py-2">
          {locales.map((code) => (
            <li key={code}>
              <button
                type="button"
                lang={code}
                aria-current={code === locale ? 'true' : undefined}
                onClick={() => change(code)}
                className={cn('w-full text-start rounded px-3 py-3 hover:bg-gray-200', code === locale && 'bg-red-200')}
              >
                {localeNames[code]}
              </button>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  );
}
