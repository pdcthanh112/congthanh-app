'use client'
import { getNotificationByCustomer } from '@/api/notificationApi';
import { Notification } from '@/models/types';
import { useQuery } from '@tanstack/react-query';
import React from 'react';
import NotificationSkeleton from './NotificationSkeleton';
import Link from 'next/link';
import Image from 'next/image';
import moment from 'moment';

export default function NotificationPage() {
  const { data: listNotification, isLoading } = useQuery({
    queryKey: ['notification'],
    queryFn: async () => await getNotificationByCustomer('').then((response) => response.data),
  });

  if (isLoading) return Array.from(Array(5)).map((_, idx) => <NotificationSkeleton key={idx} />);

  if(!listNotification) return <div>List noti empty</div>

  return (
    <React.Fragment>
      <div className="w-3/5 mx-auto">
        <h3 className="text-xl font-semibold">Notification</h3>
        {listNotification.map((item: Notification) => (
          <Link href={item.url} key={item.id}>
            <div className="hover:bg-gray-200 mb-2 border-2 border-gray-400 px-3 py-2 flex">
              <div className='w-20 h-20 relative'><Image src={item.image} alt={''} objectFit='fit' fill/></div>
              <div className='ml-2'>
                <div className='font-medium'>{item.title}</div>
                <div>{item.content}</div>
              <span className='opacity-80 text-sm'>{moment(item.createdAt).format("YYYY-MM-DD HH:mm")}</span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </React.Fragment>
  );
}
