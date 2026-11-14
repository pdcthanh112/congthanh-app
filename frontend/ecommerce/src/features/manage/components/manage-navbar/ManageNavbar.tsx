'use client';

import React from 'react';
import Image from 'next/image';
import { useSession } from 'next-auth/react';
import { useTranslations } from 'next-intl';
import { Heart, KeyRound, Landmark, MapPin, Package, ShoppingCart, UserRound, type LucideIcon } from 'lucide-react';
import DefaultImage from '@/assets/images/default-image.jpg';
import { Link, usePathname } from '@/i18n/routing';
import { cn } from '@/lib/utils';
import { PATH } from '@/utils/constants/path';

type Item = { key: string; icon: LucideIcon; url: string; nested?: boolean };

const ITEMS: Item[] = [
  { key: 'profile', icon: UserRound, url: PATH.MANAGE_PATH_URL.PROFILE },
  { key: 'change_password', icon: KeyRound, url: PATH.MANAGE_PATH_URL.CHANGE_PASSWORD, nested: true },
  { key: 'banking', icon: Landmark, url: PATH.MANAGE_PATH_URL.BANKING, nested: true },
  { key: 'address', icon: MapPin, url: PATH.MANAGE_PATH_URL.ADDRESS, nested: true },
  { key: 'cart', icon: ShoppingCart, url: PATH.CART_PATH_URL.CART },
  { key: 'order', icon: Package, url: PATH.ORDER_PATH_URL.ORDER },
  { key: 'wishlist', icon: Heart, url: PATH.WISHLIST_PATH_URL.WISHLIST },
];

const normalize = (p: string) => p.replace(/\/$/, '');

export default function ManageNavbar() {
  const { data: session } = useSession();
  const user = session?.user;
  const pathname = normalize(usePathname());
  const t = useTranslations('manage');

  return (
    <nav aria-label={t('account_menu')} className="flex flex-col">
      <div className="bg-blue-200 rounded flex items-center px-3 py-2 mb-3">
        <Image
          src={user?.image ?? DefaultImage}
          alt=""
          width={56}
          height={56}
          className="rounded-full object-cover"
        />
        <div className="flex flex-col ml-3 min-w-0">
          <span className="font-semibold truncate">{user?.name}</span>
          <span className="text-sm truncate">{user?.email}</span>
        </div>
      </div>
      <ul className="flex flex-row flex-wrap gap-2 md:flex-col md:gap-0">
        {ITEMS.map(({ key, icon: Icon, url, nested }) => {
          const active = pathname === normalize(url);
          return (
            <li key={key} className={cn(nested && 'md:ml-3')}>
              <Link
                href={url}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'flex items-center gap-2 px-3 py-2 md:py-3 rounded md:mb-2 hover:bg-blue-100',
                  active ? 'bg-blue-200' : 'bg-white'
                )}
              >
                <Icon size={18} aria-hidden />
                <span>{t(key)}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
