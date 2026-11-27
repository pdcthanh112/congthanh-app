/** Nhóm thông báo: người dùng bật/tắt từng nhóm × kênh. TRANSACTIONAL luôn gửi (không tắt được). */
export type Category = 'ORDER' | 'PAYMENT' | 'REVIEW' | 'WISHLIST' | 'PROMOTION' | 'MESSAGE' | 'SYSTEM' | 'INVENTORY';
export type Channel = 'IN_APP' | 'EMAIL' | 'SMS' | 'WEB_PUSH' | 'FCM';
export const CHANNELS: readonly Channel[] = ['IN_APP', 'EMAIL', 'SMS', 'WEB_PUSH', 'FCM'];
export const CATEGORIES: readonly Category[] = [
  'ORDER',
  'PAYMENT',
  'REVIEW',
  'WISHLIST',
  'PROMOTION',
  'SYSTEM',
  'INVENTORY',
];

/** Nhóm bắt buộc (giao dịch): không tắt được email/in-app, không bị giờ yên tĩnh chặn. */
export const TRANSACTIONAL: ReadonlySet<Category> = new Set(['ORDER', 'PAYMENT']);

export interface Message {
  title: string;
  body: string;
  /** Đường dẫn trong app (ví dụ /orders/123) để bấm vào mở đúng màn hình. */
  link: string | null;
  /** HTML cho email; không có thì dựng từ title/body. */
  html?: string;
  /** Thư tiếp thị (không giao dịch): email gắn List-Unsubscribe trỏ tới trang tuỳ chọn thông báo. */
  marketing?: boolean;
}

export interface Notification {
  id: string;
  userId: string;
  category: Category;
  /** Loại cụ thể, ví dụ ORDER_PLACED, PRICE_DROP. */
  type: string;
  title: string;
  body: string;
  link: string | null;
  /** Khoá chống trùng (event id + người nhận): cùng event giao lại không tạo thông báo thứ hai. */
  dedupKey: string;
  readAt: Date | null;
  createdAt: Date;
}

export type DeliveryStatus = 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED';

export interface Delivery {
  id: string;
  notificationId: string;
  channel: Channel;
  status: DeliveryStatus;
  attempts: number;
  lastError: string | null;
  nextAttemptAt: Date;
}

export const MAX_DELIVERY_ATTEMPTS = 5;

/**
 * Độ ưu tiên trong hàng đợi gửi (số nhỏ gửi trước, ADR 0034): email đơn hàng không bị kẹt sau một đợt gửi hàng loạt.
 */
export const DELIVERY_PRIORITY = { TRANSACTIONAL: 0, DEFAULT: 5, BULK: 9 } as const;

/** Ưu tiên của lượt gửi phát sinh từ event miền theo nhóm thông báo. */
export const priorityOf = (category: Category): number =>
  TRANSACTIONAL.has(category) ? DELIVERY_PRIORITY.TRANSACTIONAL : DELIVERY_PRIORITY.DEFAULT;

/** Backoff luỹ thừa có trần: 30s, 2m, 8m, 32m, 2h. */
export function nextAttemptDelayMs(attempts: number): number {
  return Math.min(30_000 * 4 ** Math.max(attempts - 1, 0), 2 * 3_600_000);
}
