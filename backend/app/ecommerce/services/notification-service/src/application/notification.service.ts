import { DomainError } from '@ecom/platform/errors';
import { Inject, Injectable } from '@nestjs/common';
import { type Category, CHANNELS, type Channel, type Notification } from '../domain/notification.js';
import { channelsFor, keyOf, type Preferences, type QuietHours, validateQuietHours } from '../domain/preferences.js';
import {
  CLOCK,
  type Clock,
  ID_GENERATOR,
  type IdGenerator,
  type NotifyRequest,
  READ_REPOSITORIES,
  REALTIME,
  type ReadRepositories,
  type RealtimePublisher,
  type TransactionScope,
  UNIT_OF_WORK,
  type UnitOfWork,
  type WebPushSubscription,
} from './ports.js';

export interface PreferenceToggle {
  category: Category;
  channel: Channel;
  enabled: boolean;
}

@Injectable()
export class NotificationService {
  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(READ_REPOSITORIES) readonly read: ReadRepositories,
    @Inject(REALTIME) private readonly realtime: RealtimePublisher,
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(ID_GENERATOR) private readonly ids: IdGenerator,
  ) {}

  /**
   * Tạo thông báo trong transaction của event handler: lưu in-app + xếp hàng các kênh ngoài (worker gửi sau,
   * có thử lại). Trả về thông báo mới để đẩy realtime sau khi commit; undefined nếu trùng.
   */
  async notifyIn(tx: TransactionScope, req: NotifyRequest): Promise<Notification | undefined> {
    const now = this.clock.now();
    const prefs = await tx.preferences.get(req.userId);
    const channels = channelsFor(prefs, req.category, req.channels, now);
    if (channels.length === 0) return undefined;
    return tx.notifications.insertIfNew(
      {
        id: this.ids.next(),
        userId: req.userId,
        category: req.category,
        type: req.type,
        title: req.message.title,
        body: req.message.body,
        link: req.message.link,
        dedupKey: req.dedupKey,
        readAt: null,
        createdAt: now,
      },
      channels.filter((c) => c !== 'IN_APP'),
      now,
    );
  }

  /** Đẩy realtime sau commit; lỗi Valkey không làm mất thông báo (vẫn có trong danh sách). */
  async publish(created: readonly (Notification | undefined)[]): Promise<void> {
    await Promise.allSettled(
      created.filter((n): n is Notification => !!n).map((n) => this.realtime.notificationCreated(n)),
    );
  }

  /** Admin gửi thủ công cho một người (ví dụ CSKH). */
  async send(req: NotifyRequest): Promise<Notification | undefined> {
    const n = await this.uow.run((tx) => this.notifyIn(tx, req));
    await this.publish([n]);
    return n;
  }

  list(userId: string, unreadOnly: boolean, page: number, size: number) {
    const limit = Math.min(Math.max(size, 1), 50);
    return this.read.notifications.list(userId, unreadOnly, limit, (Math.max(page, 1) - 1) * limit);
  }

  markRead(userId: string, ids: readonly string[] | 'ALL') {
    return this.read.notifications.markRead(userId, ids, this.clock.now());
  }

  async updatePreferences(userId: string, toggles: readonly PreferenceToggle[], quietHours?: QuietHours | null) {
    const current = await this.read.preferences.get(userId);
    const disabled = new Set(current.disabled);
    for (const t of toggles) {
      if (!CHANNELS.includes(t.channel)) throw DomainError.invalid(`unknown channel ${t.channel}`);
      if (t.enabled) disabled.delete(keyOf(t.category, t.channel));
      else disabled.add(keyOf(t.category, t.channel));
    }
    const next: Preferences = {
      userId,
      disabled,
      quietHours: quietHours === undefined ? current.quietHours : validateQuietHours(quietHours),
    };
    await this.read.preferences.save(next);
    return next;
  }

  registerWebPush(userId: string, s: WebPushSubscription) {
    if (!s.endpoint.startsWith('https://')) throw DomainError.invalid('push endpoint must be https');
    return this.read.devices.addWebPush(userId, s);
  }

  registerFcmToken(userId: string, token: string, platform: string) {
    if (!token.trim()) throw DomainError.invalid('token is required');
    return this.read.devices.addFcmToken(userId, token.trim(), platform);
  }
}
