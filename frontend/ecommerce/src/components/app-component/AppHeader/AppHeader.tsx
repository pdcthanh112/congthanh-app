'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import Image from 'next/image';
import { useTranslations } from 'next-intl';
import { ChevronDown, LayoutGrid, LogOut, Settings } from 'lucide-react';
import AppLogo from '@/assets/images/app-logo-removebg.png';
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui';
import LanguageSwitcher from '@/components/language-switcher';
import SearchComponent from '@/components/Search/SearchComponent';
import { ThemeToggle } from '@/components/Theme/ThemeToggle';
import CartPopover from '@/features/cart/component/CartPopover';
import NotificationPopover from '@/features/notification/NotificationPopover';
import { useAuthenticated } from '@/hooks/auth/useAuthenticated';
import { Link } from '@/i18n/routing';
import { login, logout, register } from '@/lib/auth/actions';
import { PATH } from '@/utils/constants/path';

const CategoryComponent = dynamic(() => import('@/components/Category/CategoryComponent'), { ssr: false });

const initials = (name?: string | null) =>
  (name ?? '?')
    .split(' ')
    .filter(Boolean)
    .slice(-2)
    .map((w) => w[0]?.toUpperCase())
    .join('');

export default function AppHeader() {
  const { user, isAuthenticated } = useAuthenticated();
  const t = useTranslations();

  return (
    <header className="bg-slate-400 text-white px-2 sm:px-4 py-2">
      <div className="flex items-center gap-2 sm:gap-4 min-h-16">
        <Link href={PATH.HOME} className="shrink-0">
          <Image src={AppLogo} alt="CongThanhApp" width={100} className="w-16 sm:w-24 h-auto" priority />
        </Link>

        <Popover>
          <PopoverTrigger asChild>
            <button type="button" className="hidden sm:flex p-1 rounded hover:bg-slate-500" aria-label={t('header.categories')}>
              <LayoutGrid aria-hidden />
            </button>
          </PopoverTrigger>
          <PopoverContent className="w-[min(80rem,calc(100vw-2rem))] p-0" align="start">
            <CategoryComponent />
          </PopoverContent>
        </Popover>

        <SearchComponent className="hidden md:flex" />

        <div className="ml-auto flex items-center text-xs gap-4 sm:gap-6 whitespace-nowrap">
          <LanguageSwitcher />
          <CartPopover />
          <ThemeToggle />
          <NotificationPopover />

          {isAuthenticated && user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className="flex items-center gap-2 bg-green-500 px-2 py-1 rounded-full"
                  aria-label={t('header.account_menu')}
                >
                  <Avatar className="size-8">
                    {user.image && <AvatarImage src={user.image} alt="" />}
                    <AvatarFallback className="text-black">{initials(user.name)}</AvatarFallback>
                  </Avatar>
                  <ChevronDown size={16} aria-hidden />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-72">
                <DropdownMenuLabel className="flex flex-col">
                  <span className="font-medium truncate">{user.name}</span>
                  <span className="text-xs font-normal text-gray-500 truncate">{user.email}</span>
                </DropdownMenuLabel>
                <DropdownMenuItem asChild>
                  <Link href={PATH.MANAGE_PATH_URL.PROFILE}>{t('common.manage_profile')}</Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuGroup>
                  <DropdownMenuLabel className="text-xs text-gray-500">{t('header.your_shopping')}</DropdownMenuLabel>
                  <DropdownMenuItem asChild>
                    <Link href={PATH.CART_PATH_URL.CART}>{t('header.view_your_cart')}</Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <Link href={PATH.ORDER_PATH_URL.ORDER}>{t('header.orders')}</Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <Link href={PATH.WISHLIST_PATH_URL.WISHLIST}>{t('header.wishlist')}</Link>
                  </DropdownMenuItem>
                </DropdownMenuGroup>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link href={PATH.MANAGE_PATH_URL.ADDRESS}>
                    <Settings size={16} aria-hidden />
                    {t('header.account')}
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => logout()}>
                  <LogOut size={16} aria-hidden />
                  {t('auth.logout')}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <div className="flex flex-col">
              <span className="hidden sm:inline">{t('common.welcome')}</span>
              <span className="font-semibold md:text-sm">
                {t.rich('header.login_or_register', {
                  login: () => (
                    <button type="button" className="hover:underline" onClick={() => login()}>
                      {t('auth.login')}
                    </button>
                  ),
                  register: () => (
                    <button type="button" className="hover:underline" onClick={() => register()}>
                      {t('auth.register')}
                    </button>
                  ),
                })}
              </span>
            </div>
          )}
        </div>
      </div>
      {/* Mobile: ô tìm kiếm xuống dòng riêng. */}
      <SearchComponent className="flex md:hidden mt-2" />
    </header>
  );
}
