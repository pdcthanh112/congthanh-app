import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Notification as NotificationType } from '@/models/types';
import { PATH } from '@/utils/constants/path';
import NotificationItem from './NotificationItem';
import NotificationItemSkeleton from './NotificationItemSkeleton';
import { useTranslations } from 'next-intl';
import { useAuthenticated } from '@/hooks/auth/useAuthenticated';
import { getToken, onMessage } from 'firebase/messaging';
import { messaging } from '@/config/firebaseConfig';
import { getNotificationByCustomer } from '@/api/notificationApi';

type PropsType = {
  listNotification: Notification[];
  loading: boolean;
};

export default function NotificationModal({ listNotification, loading = true }: PropsType) {
  const { user } = useAuthenticated();
  const t = useTranslations();

  const [notifications, setNotifications] = useState<NotificationType[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [showDropdown, setShowDropdown] = useState(false);

  useEffect(() => {
    const fetchNotifications = async () => {
      try {
        const data = await getNotificationByCustomer('e6169aa0-d4a5-4740-b510-9285bbb73a0e').then((res) => res.data);

        setNotifications(data);
        setUnreadCount(data.filter((notif) => !notif.isRead).length);
      } catch (error) {
        console.error('Fetch notifications failed:', error);
      }
    };
    fetchNotifications();
  }, []);

  useEffect(() => {
    // Đăng ký FCM token
    // getToken(messaging, { vapidKey: 'BIuwuqkeU8JskcKBy7J7VeCF_j6__UzkBNRgMxzV74hdLE_Eglzu2Ov88V4HAQ1wt69weVf2C84T_3D_LUOvX-I' })
    //   .then((token) => {
    //     // Gửi token đến backend
    //     console.log('FCM Token:', token); // In token ra console
    //     // axios.post('http://your-backend:8080/api/notifications/register-device', {
    //     //   user.accountId,
    //     //   fcmToken: token,
    //     // });
    //   })
    //   .catch((error) => {
    //     console.error('FCM token registration failed:', error);
    //   });
const requestNotificationPermission = async () => {
      try {
        const permission = await Notification.requestPermission();
 
        if (permission === 'granted') {
          // Đăng ký FCM token
          const vapidKey = 'BIuwuqkeU8JskcKBy7J7VeCF_j6__UzkBNRgMxzV74hdLE_Eglzu2Ov88V4HAQ1wt69weVf2C84T_3D_LUOvX-I';
          const token = await getToken(messaging, { vapidKey });
          console.log('FCM Tokenmmmmmmmmmmmmmmmmmmmmmmm:', token);// ejLC3iHQBNKGEtV5ezJaSB:APA91bFIGpIrkoGdygX1qnOdoMClANMI5MRmJtJUykXmuKGpYkwbuMxUOdC_pEtMy1hLmvZa1K60qJ1YoOKQENO1gyBoH65oRVWWdQ85l5t-VpMHUSDfs1M
          // Gửi token đến backend
          // await axios.post('http://your-backend:8080/api/notifications/register-device', {
          //   userId,
          //   fcmToken: token,
          // });
        }
      } catch (error) {
        console.error('FCM token registration failed:', error);
      }
    };

    // Gọi hàm yêu cầu quyền
    requestNotificationPermission();
    // Nhận thông báo thời gian thực
    onMessage(messaging, (payload) => {
      const newNotif = {
        id: Date.now(),
        message: payload.data.message,
        createdAt: payload.data.timestamp,
        isRead: false,
      };
      // setNotifications((prev) => [newNotif, ...prev]);
      // setUnreadCount((prev) => prev + 1);
    });
  }, []);

  if (!user) return <div>Needed signin</div>;

  return (
    <div>
      <h3 className="font-semibold text-lg pl-3">{t('common.notification')}</h3>
      <div className="text-end">
        <Link href={PATH.NOTIFICATION_PATH_URL.NOTIFICATION} className="mr-5 hover:underline hover:text-yellow-400">
          {t('common.see_all')}
        </Link>
      </div>
      {/* {loading ? (
        Array.from(Array(4)).map((_, id) => (
          <div key={id} className="px-4 py-2 w-full">
            <NotificationItemSkeleton />
          </div>
        ))
      ) : listNotification.length > 0 ? (
        listNotification?.map((item: Notification) => <NotificationItem key={item.id} item={item} />)
      ) : (
        <div>you have no noti</div>
      )} */}
    </div>
  );
}
