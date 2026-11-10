'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import './AppHeader.scss';
import Image from 'next/image';
import { Card, Icon } from '@mui/material';
import { useRouter } from 'next/navigation';
import { useAppDispatch } from '@/redux/store';
import AppLogo from '@/assets/images/app-logo-removebg.png';
import { NavigateNext, Category as CategoryIcon } from '@mui/icons-material';
import CartModal from '@/features/cart/component/CartModal';
import LanguageSwitcher from '@/components/language-switcher';
import { PATH } from '@/utils/constants/path';
import { NotificationIcon } from '@/assets/icons';
import { logoutRequested } from '@/redux/actions/auth';
import Link from 'next/link';
import { Notification } from '@/models/types';
import { ThemeToggle } from '../../theme/ThemeToggle';
import { useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { NOTIFICATION_KEY } from '@/utils/constants/queryKey';
import { getNotificationByCustomer } from '@/api/notificationApi';
import { Popover, PopoverContent, PopoverTrigger, Avatar, AvatarFallback, AvatarImage, Button } from '@/components/ui';
import { useAuthenticated } from '@/hooks/auth/useAuthenticated';
import SearchComponent from '../../Search/SearchComponent';
import { ChevronDown, LucideIcon, Settings } from 'lucide-react';

const CategoryComponent = dynamic(() => import('../../Category/CategoryComponent'), { ssr: false });
const NotificationModal = dynamic(() => import('@/features/notification/components/notification-modal'), {
  ssr: false,
  loading: () => <div>Loading...</div>,
});

export default function AppHeader() {
  const dispatch = useAppDispatch();
  const router = useRouter();
  const { user, isAuthenticated, logout } = useAuthenticated();
  const t = useTranslations();

  const { data: listNotification, isLoading } = useQuery({
    queryKey: [NOTIFICATION_KEY],
    queryFn: async () =>
      await getNotificationByCustomer('e6169aa0-d4a5-4740-b510-9285bbb73a0e').then((response) => response.data),
    // await getNotificationByCustomer(currentUser.userInfo.accountId).then((response) => response.data),
  });

  const handleLogout = () => {
    logout();
    dispatch(logoutRequested({ email: 'fjasljflashfiahsd' }));
  };

  return (
    <div className="flex items-center bg-slate-400 p-1 grow py-2 h-20">
      <Link href={PATH.HOME}>
        <Image src={AppLogo} alt="App Logo" width={100} className="cursor-pointer mx-12" />
      </Link>

      <div className="hidden sm:flex items-center mr-5">
        <Popover>
          <PopoverTrigger asChild>
            <CategoryIcon className="hover:cursor-pointer" />
          </PopoverTrigger>
          <PopoverContent className="w-[80rem] overflow-y-scroll" align="center" side="bottom">
            <CategoryComponent />
          </PopoverContent>
        </Popover>
      </div>

      <SearchComponent />

      <div className="text-white flex items-center text-xs space-x-6 mx-6 whitespace-nowrap">
        <LanguageSwitcher />

        <CartModal />

        <ThemeToggle />

        <Popover>
          <PopoverTrigger asChild>
            <div className="flex items-start justify-center relative">
              <NotificationIcon width={30} height={30} className="hover:cursor-pointer" />
              {Number(listNotification?.length) > 0 && (
                <span className="absolute top-0 right-0 h-4 w-4 bg-yellow-400 text-center rounded-full text-black font-bold">
                  {listNotification?.filter((item) => item.isRead === false).length}
                </span>
              )}
            </div>
          </PopoverTrigger>
          <PopoverContent className="w-[25rem]">
            <NotificationModal listNotification={listNotification as Notification[]} loading={isLoading} />
          </PopoverContent>
        </Popover>

        <div className="relative inline-block group">
          <div className="hover:cursor-pointer">
            {isAuthenticated && user ? (
              <React.Fragment>
                <div className="flex items-center bg-green-400 px-2 py-1 rounded-full">
                  <Avatar style={{ width: '2rem', height: '2rem' }}>
                    <AvatarImage src={user?.image!!} alt="" />
                    <AvatarFallback>{user.name?.split(' ').pop()}</AvatarFallback>
                  </Avatar>
                  <ChevronDown className="ml-3" />
                </div>
                <Card className="text-[#a4a4a4] text-sm hidden absolute transform -translate-x-96 p-4 w-[30rem] group-hover:block group-hover:z-50">
                  <div className="flex justify-between bg-sky-100 px-5 py-3 rounded-md">
                    <div className="flex items-center">
                      <Avatar>
                        <AvatarImage src={user?.image!!} alt="" />
                        <AvatarFallback>{user.name?.split(' ').pop()}</AvatarFallback>
                      </Avatar>
                      <div className="ml-3 flex flex-col">
                        <span className="font-medium">{user.name}</span>
                        <span className="text-xs">pdcthanh112.dev@gmail.com</span>
                        <span className="text-xs">Customer id: #16494681</span>
                      </div>
                    </div>
                    <Link
                      href={PATH.MANAGE_PATH_URL.PROFILE}
                      className="flex items-center hover:cursor-pointer hover:underline hover:text-yellow-600"
                    >
                      {t('common.manage_profile')}
                      <Icon component={NavigateNext} />
                    </Link>
                  </div>
                  <div className="flex">
                    <div className="w-1/2">
                      <h3 className="font-semibold text-base">{t('header.your_shopping')}</h3>
                      <menu className="leading-6">
                        <MenuItem name={t('header.view_your_cart')} url={PATH.CART_PATH_URL.CART} />
                        <MenuItem name={t('header.orders')} url={PATH.ORDER_PATH_URL.ORDER} />
                        <MenuItem name={t('header.wishlist')} url={PATH.WISHLIST_PATH_URL.WISHLIST} />
                        <MenuItem name={t('header.history')} url={PATH.HISTORY_PATH_URL.HISTORY} />
                      </menu>
                    </div>
                    <div className="w-1/2 border-l-2 border-l-gray-100 pl-4">
                      {/* <h3 className="font-semibold text-base">{t('header.your_account')}</h3> */}
                      <menu className="">
                        <MenuItem1 name={t('header.account')} url={PATH.MANAGE_PATH_URL.ACCOUNT} icon={Settings} />
                        <MenuItem1 name={t('header.recommendations')} url={'#'} icon={ChevronDown} />
                        <MenuItem1 name={t('header.browsing_history')} url={'#'} icon={ChevronDown} />
                        <MenuItem1 name={t('header.watchlist')} url={'#'} icon={ChevronDown} />
                      </menu>
                      <Button className="w-full" onClick={() => handleLogout()}>
                        {t('auth.logout')}
                      </Button>
                    </div>
                  </div>
                </Card>
              </React.Fragment>
            ) : (
              <React.Fragment>
                <div>{t('common.welcome')}</div>
                <div className="font-semibold md:text-sm" onClick={() => router.push(PATH.AUTH_PATH_URL.LOGIN)}>
                  {t('auth.login')} or {t('auth.register')}
                </div>
                <Card className="text-[#a4a4a4] text-sm hidden absolute transform -translate-x-3/4 p-4 w-[25rem] group-hover:block group-hover:z-50">
                  jasdfjkla alsjsajf lạdljaf ládjflajfl; allsdjlfjl
                </Card>
              </React.Fragment>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

const MenuItem = ({ name, url, className }: { name: string; url: string; className?: string }) => (
  <li>
    <Link href={url} className={`opacity-80 hover:opacity-90 hover:underline hover:text-blue-600 ${className}`}>
      {name}
    </Link>
  </li>
);

const MenuItem1 = ({
  name,
  url,
  className,
  icon: Icon,
}: {
  name: string;
  url: string;
  className?: string;
  icon: LucideIcon;
}) => (
  <li>
    <Link
      href={url}
      className={`flex items-center my-2 bg-gray-50 px-3 py-2 rounded opacity-80 hover:opacity-90 hover:text-green-400 hover:bg-gray-100 ${className}`}
    >
      <Icon size={16} />
      <span className="ml-1">{name}</span>
    </Link>
  </li>
);
