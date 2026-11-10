'use client'

import React from 'react';
import Image from 'next/image';
import { TableContainer, Paper, Table, TableHead, TableRow, TableBody, TableCell } from '@mui/material';
import { Customer, Product } from '@/models/types';
import { useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { getWishlistByCustomer } from '@/api/wishlistApi';
import EmptyWishlistImage from '@/assets/images/empty_wishlist.png';
import { WISHLIST_KEY } from '@/utils/constants/queryKey';
import { useAppSelector } from '@/redux/store';
import WishlistItem from './WishlistItem';
import Link from 'next/link';
import { PATH } from '@/utils/constants/path';


const ShowWishlistItem = () => {
  const currentUser: Customer = useAppSelector((state) => state.auth.currentUser);
  const t = useTranslations();

  const { data: wishlist, isLoading } = useQuery({
    queryKey: [WISHLIST_KEY],
    queryFn: async () => await getWishlistByCustomer(currentUser.userInfo.accountId).then((response) => response.data)
  });

  const EmptyWishlist = () => {
    return (
      <div style={{ width: '250%' }}>
        <div className="flex justify-center">
          <Image src={EmptyWishlistImage} alt={'Empty wishlist'} width={300} height={300} />
        </div>
  
        <Link href={PATH.HOME} className="flex justify-center hover:cursor-pointer hover:underline">
          {t('common.back_to_home')}
        </Link>
      </div>
    );
  };

  return (
    <div className="bg-white w-full flex justify-center">
      <TableContainer component={Paper} style={{ width: '90%', margin: '10px 0 10px 0' }}>
        <Table style={{ width: '85vw', margin: '0 auto' }}>
          <TableHead>
            <TableRow>
              <TableCell style={{ paddingLeft: '10rem', width: '40%' }}>{t('product.product_name')}</TableCell>
              <TableCell align="center" width={'15%'}>
                {t('common.Category')}
              </TableCell>
              <TableCell align="center" width={'10%'}>
                {t('product.price')}
              </TableCell>
              <TableCell align="center" width={'10%'}>
                {t('common.status')}
              </TableCell>
              <TableCell align="center" width={'11%'} />
            </TableRow>
          </TableHead>
          {wishlist ? (
            <TableBody>
              {wishlist.product.map((item: Product) => (
                <WishlistItem key={item.id} item={item} />
              ))}
            </TableBody>
          ) : (
            <TableBody>
              <EmptyWishlist />
            </TableBody>
          )}
        </Table>
      </TableContainer>
    </div>
  );
};

export default ShowWishlistItem;
