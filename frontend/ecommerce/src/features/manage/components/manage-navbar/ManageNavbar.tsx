import React from 'react';
import Image from 'next/image';
import DefaultImage from '@/assets/images/default-image.jpg';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Icon } from '@mui/material';
import { AccountBox, ShoppingCart, Favorite, Lock, AddCard, LocationOn } from '@mui/icons-material';
import { PATH } from '@/utils/constants/path';
import { useTranslations } from 'next-intl';

type NavBarItemProps = {
  name: string;
  icon: any;
  url: string;
  className?: string;
};

export default function ManageNavbar() {
  // const user = getServerSession()
  const user = {
    image: null,
    name: null
  };
  const pathname = usePathname();

  const t = useTranslations();

  const NavBarItem = ({ name, icon, url, className }: NavBarItemProps) => (
    // <Card className={`px-3 py-4 rounded mb-2 hover:bg-blue-200  ${pathname === url && 'bg-blue-200'} ${className}`} sx={{backgroundColor: pathname === url ? '#4d4dff' : '#fff'}}>
    <Link
      href={url}
      className={`flex px-3 py-4 rounded mb-2 hover:bg-blue-100  ${
        pathname === url ? 'bg-blue-200' : 'bg-white'
      } ${className}`}
    >
      <Icon component={icon} />
      <span className="ml-1">{name}</span>
    </Link>
    // </Card>
  );

  return (
    <div className="flex flex-col">
      <div className="bg-blue-200 rounded flex px-3 py-2 border-b-2 border-gray-300 pb-2 mb-3">
        <Image
          src={user.image ?? DefaultImage}
          alt={user.name ?? ''}
          width={60}
          height={60}
          className="rounded-[100%]"
        />
        <div className="flex flex-col ml-3">
          <div className="font-semibold flex items-center">{user.name}</div>
          <div className="flex flex-col text-sm">
            <span>{user.email}</span>
            <span>{user.phone}</span>
          </div>
        </div>
      </div>
      <div className="flex flex-col">
        <NavBarItem name={t('manage.profile')} icon={AccountBox} url={PATH.MANAGE_PATH_URL.PROFILE} />

        <div className="ml-3 flex flex-col">
          <NavBarItem name={'Change password'} icon={Lock} url={PATH.MANAGE_PATH_URL.CHANGE_PASSWORD} />
          <NavBarItem name={'Banking account'} icon={AddCard} url={PATH.MANAGE_PATH_URL.BANKING} />
          <NavBarItem name={'Address'} icon={LocationOn} url={PATH.MANAGE_PATH_URL.ADDRESS} />
        </div>
      </div>
      <NavBarItem name={'Cart'} icon={ShoppingCart} url={PATH.CART_PATH_URL.CART} />

      <NavBarItem name="Order" icon={ShoppingCart} url={PATH.ORDER_PATH_URL.ORDER} />

      <NavBarItem name="Wishlist" icon={Favorite} url={PATH.WISHLIST_PATH_URL.WISHLIST} />
    </div>
  );
}
