import type { Broadcast, BroadcastAudience } from '../domain/broadcast.js';
import type { Category, Channel, Delivery, DeliveryStatus, Message, Notification } from '../domain/notification.js';
import type { Preferences } from '../domain/preferences.js';

/** Một thông báo kèm các kênh ngoài cần xếp hàng gửi. */
export interface NewNotification {
  notification: Notification;
  channels: readonly Channel[];
}

/** Cách xếp hàng lượt gửi: ưu tiên (số nhỏ gửi trước) và chiến dịch sinh ra nó, nếu có. */
export interface EnqueueOptions {
  priority: number;
  broadcastId?: string;
}

export interface NotificationRepository {
  /** Chèn nếu dedupKey chưa có; trả undefined nếu đã tồn tại (event giao lại). */
  insertIfNew(
    n: Notification,
    channels: readonly Channel[],
    now: Date,
    opts: EnqueueOptions,
  ): Promise<Notification | undefined>;
  /**
   * Chèn cả lô trong một câu lệnh, bỏ qua dedupKey đã có (idempotent khi chạy lại một trang người nhận).
   * Trả về các thông báo vừa tạo thật.
   */
  insertManyIfNew(items: readonly NewNotification[], now: Date, opts: EnqueueOptions): Promise<Notification[]>;
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
  /** Tuỳ chọn của nhiều người một lần; người chưa lưu tuỳ chọn nhận mặc định. */
  getMany(userIds: readonly string[]): Promise<Map<string, Preferences>>;
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
  claimDue(channel: Channel, limit: number, now: Date, lease: Date): Promise<Delivery[]>;
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

/** Số lượt gửi của một chiến dịch theo kênh và trạng thái. */
export interface DeliveryCount {
  channel: Channel;
  status: DeliveryStatus;
  count: number;
}

/** Chiến dịch gửi hàng loạt (ADR 0034). */
export interface BroadcastRepository {
  /** Lưu chiến dịch mới; đã có (createdBy, idempotencyKey) thì không lưu mà trả chiến dịch cũ. */
  insertOrGet(b: Broadcast, idempotencyKey: string): Promise<Broadcast>;
  get(id: string): Promise<Broadcast | undefined>;
  /** Đọc và khoá hàng (FOR UPDATE) cho tới hết transaction. */
  lock(id: string): Promise<Broadcast | undefined>;
  list(limit: number, offset: number): Promise<{ items: Broadcast[]; total: number }>;
  /** Lấy một chiến dịch đến hạn cần chia trang và giữ lease tới `lease`; pod khác bỏ qua (SKIP LOCKED). */
  claimDue(now: Date, lease: Date): Promise<Broadcast | undefined>;
  /** Ghi xong một trang người nhận: dời cursor, cộng số người nhận, gia hạn lease, chuyển EXPANDING. */
  advance(id: string, cursor: number, recipientsAdded: number, lease: Date): Promise<void>;
  /** Đã xếp hàng hết người nhận. */
  markSending(id: string): Promise<void>;
  /** Huỷ chiến dịch và bỏ (SKIPPED) các lượt gửi còn chờ của nó; trả số lượt đã bỏ. */
  cancel(id: string, at: Date): Promise<number>;
  /** Chuyển COMPLETED các chiến dịch SENDING không còn lượt gửi chờ; trả id các chiến dịch vừa xong. */
  completeFinished(at: Date): Promise<string[]>;
  deliveryCounts(id: string): Promise<DeliveryCount[]>;
}

export interface TransactionScope {
  notifications: NotificationRepository;
  preferences: PreferenceRepository;
  views: LocalViews;
  broadcasts: BroadcastRepository;
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
  broadcasts: BroadcastRepository;
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
  /**
   * Một trang người nhận của chiến dịch theo vị trí (first, max), gồm cả người bị khoá (gọi bên lọc).
   * Trang ngắn hơn `max` nghĩa là đã hết.
   */
  page(audience: BroadcastAudience, first: number, max: number): Promise<Recipient[]>;
}

/** Hạn mức gửi theo kênh dùng chung cho mọi pod, để không vượt giới hạn của nhà cung cấp (ADR 0034). */
export interface SendRateLimiter {
  /** Xin tối đa `wanted` lượt gửi trong cửa sổ hiện tại; trả số lượt được phép (0 tới `wanted`). */
  acquire(channel: Channel, wanted: number): Promise<number>;
  /** Trả lại lượt đã xin mà không dùng tới. */
  release(channel: Channel, unused: number): Promise<void>;
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
export const SEND_RATE_LIMITER = Symbol('SEND_RATE_LIMITER');
