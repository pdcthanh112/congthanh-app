import React from 'react';
import { Notification } from '@/models/types';
import Image from 'next/image';
import moment from 'moment';
import { IMAGE } from '@/utils/constants/path';
import { Icon } from '@mui/material';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { CancelPresentation, DoneAll, List } from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { NOTIFICATION_KEY } from '@/utils/constants/queryKey';
import { changeNotificationReadingStatus } from '@/api/notificationApi';

type PropsType = {
  item: Notification;
};

const NotificationItem = ({ item }: PropsType) => {
  const queryClient = useQueryClient();

  const { mutate: handleChangeReadingStatus } = useMutation({
    mutationKey: [NOTIFICATION_KEY],
    mutationFn: async (params: { notificationId: number; status: boolean }) => {
      await changeNotificationReadingStatus(params.notificationId, params.status);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [NOTIFICATION_KEY] });
    },
  });

  return (
    <div
      className={`px-4 py-2 hover:cursor-pointer hover:bg-slate-50 flex relative ${!item.isRead && 'bg-green-50'}`}
    >
      <Image src={IMAGE.defaultImage} alt={''} width={70} height={70} className="rounded-full" />
      <div className="mx-3 w-[65%]">
        <div className="font-medium truncate" title={item.title}>
          {item.title}
        </div>
        <div className="text-sm">{item.content}</div>
        <div className="text-end text-sm absolute right-3 bottom-2 opacity-80 italic">
          {moment(item.createdAt).fromNow()}
        </div>
      </div>

      <Popover>
        <PopoverTrigger asChild>
          <Icon component={List} fontSize="small" />
        </PopoverTrigger>
        <PopoverContent className="">
          <div
            className="hover:bg-gray-200 hover:cursor-pointer px-3 py-2 rounded"
            onClick={() => handleChangeReadingStatus({ notificationId: item.id, status: !item.isRead })}
          >
            <Icon component={DoneAll} className="mr-3" />
            {item.isRead ? 'Mask as unread' : 'Mask as read'}
          </div>
          <div className="hover:bg-gray-200 hover:cursor-pointer px-3 py-2 rounded">
            <Icon component={CancelPresentation} className="mr-3" />
            Delete notification
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
};

export default NotificationItem;
