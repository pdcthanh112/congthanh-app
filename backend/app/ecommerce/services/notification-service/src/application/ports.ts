import type { Category, Channel, Delivery, Message, Notification } from '../domain/notification.js';
import type { Preferences } from '../domain/preferences.js';

export interface NotificationRepository {
  /** Chèn nếu dedupKey chưa có; trả undefined nếu đã tồn tại (event giao lại). */
  insertIfNew(n: Notification, channels: readonly Channel[], now: Date): Promise<Notification | undefined>;
  list(
    userId: string,
    unreadOnly: boolean,
    limit: number,
    offset: number,
  ): Promise<{ items: Notification[]; total: number }>;
  unreadCount(userId: string): Promise<number>;
  markRead(userId: string, ids: readonly string[] | 'ALL', at: Date): Promise<number>;
  get(id: string): Promise<Notification | undefined>;
}

export interface PreferenceRepository {
  get(userId: string): Promise<Preferences>;
  save(p: Preferences): Promise<void>;
}

export interface WebPushSubscription {
  endpoint: string;
  p256dh: string;
  auth: string;
}

export interface DeviceRepository {
  addWebPush(userId: string, s: WebPushSubscription): Promise<void>;
  removeWebPush(endpoint: string): Promise<void>;
  webPushOf(userId: string): Promise<WebPushSubscription[]>;
  addFcmToken(userId: string, token: string, platform: string): Promise<void>;
  removeFcmToken(token: string): Promise<void>;
  fcmTokensOf(userId: string): Promise<string[]>;
}

export interface DeliveryQueue {
  /** Lấy các lượt gửi đến hạn và khoá lại (SKIP LOCKED: nhiều pod chạy song song không gửi trùng). */
  claimDue(limit: number, now: Date, lease: Date): Promise<Delivery[]>;
  markSent(id: string): Promise<void>;
  markSkipped(id: string, reason: string): Promise<void>;
  markFailed(id: string, error: string, retryAt: Date | null): Promise<void>;
}

/** Bản sao cục bộ dựng từ event để biết gửi cho ai (không gọi đồng bộ sang service khác). */
export interface LocalViews {
  rememberOrder(orderId: string, customerId: string): Promise<void>;
  customerOfOrder(orderId: string): Promise<string | undefined>;
  watch(customerId: string, productId: string): Promise<void>;
  unwatch(customerId: string, productId: string): Promise<void>;
  watchers(productId: string): Promise<string[]>;
  /** Cập nhật giá/tồn mới nhất, trả về giá trị cũ để phát hiện giảm giá/có hàng lại. */
  updateProduct(
    productId: string,
    patch: { name?: string; priceMinor?: number; currency?: string; available?: number },
  ): Promise<
    { name: string | null; priceMinor: number | null; currency: string | null; available: number | null } | undefined
  >;
}

export interface TransactionScope {
  notifications: NotificationRepository;
  preferences: PreferenceRepository;
  views: LocalViews;
  markProcessed(eventId: string, consumer: string): Promise<boolean>;
}

export interface UnitOfWork {
  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T>;
}

export interface ReadRepositories {
  notifications: NotificationRepository;
  preferences: PreferenceRepository;
  devices: DeviceRepository;
  deliveries: DeliveryQueue;
}

export interface Recipient {
  id: string;
  email?: string;
  phone?: string;
  name?: string;
  enabled: boolean;
}

/** Thông tin liên hệ (email, số điện thoại) lấy từ Keycloak theo id người dùng. */
export interface RecipientDirectory {
  find(userId: string): Promise<Recipient | undefined>;
  /** Người dùng có realm role (ví dụ ADMIN để nhận cảnh báo tồn kho). */
  withRole(role: string): Promise<string[]>;
}

/** Lỗi gửi vĩnh viễn (không thử lại): địa chỉ không tồn tại, token hết hạn... */
export class PermanentDeliveryError extends Error {}
/** Người nhận không có địa chỉ cho kênh này (chưa có email/số điện thoại/thiết bị): bỏ qua, không phải lỗi. */
export class NoAddressError extends PermanentDeliveryError {}

export interface ChannelSender {
  readonly channel: Channel;
  send(recipient: Recipient, message: Message, devices: DeviceRepository): Promise<void>;
}

export interface RealtimePublisher {
  notificationCreated(n: Notification): Promise<void>;
  /** Luồng thông báo mới của một người dùng (cho GraphQL subscription), dùng được trên nhiều pod. */
  stream(userId: string): AsyncIterableIterator<Notification>;
}

export interface Clock {
  now(): Date;
}
export interface IdGenerator {
  next(): string;
}

export interface NotifyRequest {
  userId: string;
  category: Category;
  type: string;
  message: Message;
  dedupKey: string;
  channels: readonly Channel[];
}

export const UNIT_OF_WORK = Symbol('UNIT_OF_WORK');
export const READ_REPOSITORIES = Symbol('READ_REPOSITORIES');
export const RECIPIENT_DIRECTORY = Symbol('RECIPIENT_DIRECTORY');
export const CHANNEL_SENDERS = Symbol('CHANNEL_SENDERS');
export const REALTIME = Symbol('REALTIME');
export const CLOCK = Symbol('CLOCK');
export const ID_GENERATOR = Symbol('ID_GENERATOR');
