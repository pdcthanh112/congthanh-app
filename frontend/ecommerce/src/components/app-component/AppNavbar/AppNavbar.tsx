'use client';
import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Menu } from 'lucide-react';
import { Dialog, DialogContent, DialogTitle, DialogTrigger } from '@/components/ui';
import AppSidebar from '@/components/app-component/AppSidebar';
import { Link } from '@/i18n/routing';

const AppNavbar = () => {
  const t = useTranslations();
  const [open, setOpen] = useState(false);

  const navbarItems = [
    { name: t('navbar.today_deals'), path: '/home' },
    { name: t('navbar.flash_sale'), path: '/home' },
    { name: t('navbar.gift_card'), path: '/home' },
    { name: t('navbar.sale'), path: '/home' },
  ];

  return (
    <nav aria-label={t('header.main_navigation')} className="flex items-center bg-blue-300 text-white gap-4 sm:gap-6 p-2 pl-4 overflow-x-auto whitespace-nowrap">
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger asChild>
          <button type="button" className="flex items-center gap-1 font-medium" aria-label={t('common.open_menu')}>
            <Menu className="h-5 w-5" aria-hidden />
            {t('common.all')}
          </button>
        </DialogTrigger>
        {/* Drawer trượt từ trái: dùng Dialog của Radix để có focus trap, Esc để đóng. */}
        <DialogContent className="left-0 top-0 translate-x-0 translate-y-0 h-full max-w-none w-auto p-0 rounded-none border-0 sm:max-w-none data-[state=open]:slide-in-from-left">
          <DialogTitle className="sr-only">{t('common.menu')}</DialogTitle>
          <AppSidebar onNavigate={() => setOpen(false)} />
        </DialogContent>
      </Dialog>
      {navbarItems.map((item, id) => (
        <Link key={id} href={item.path} className="hover:underline focus:underline">
          {item.name}
        </Link>
      ))}
    </nav>
  );
};

export default AppNavbar;
