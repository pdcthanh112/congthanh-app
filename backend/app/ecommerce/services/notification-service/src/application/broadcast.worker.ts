import { Inject, Injectable, Logger, type OnApplicationBootstrap, type OnApplicationShutdown } from '@nestjs/common';
import { type Broadcast, broadcastDedupKey } from '../domain/broadcast.js';
import { DELIVERY_PRIORITY, type Notification } from '../domain/notification.js';
import { channelsFor } from '../domain/preferences.js';
import { NotificationService } from './notification.service.js';
import {
  CLOCK,
  type Clock,
  ID_GENERATOR,
  type IdGenerator,
  type NewNotification,
  READ_REPOSITORIES,
  RECIPIENT_DIRECTORY,
  type ReadRepositories,
  type Recipient,
  type RecipientDirectory,
  type TransactionScope,
  UNIT_OF_WORK,
  type UnitOfWork,
} from './ports.js';

export const BROADCAST_WORKER_OPTIONS = Symbol('BROADCAST_WORKER_OPTIONS');
export interface BroadcastWorkerOptions {
  enabled: boolean;
  intervalMs: number;
  /** Số người nhận mỗi trang, mỗi trang một transaction. */
  pageSize: number;
}

/** Pod đang chia trang giữ chiến dịch tới lúc này; chết giữa chừng thì pod khác tiếp tục sau khi hết hạn. */
const LEASE_MS = 60_000;

/**
 * Chia người nhận của chiến dịch đến hạn thành từng trang (ADR 0034). Mỗi trang một transaction: lọc theo tuỳ chọn
 * người dùng, tạo thông báo + xếp hàng lượt gửi ưu tiên thấp, dời cursor. Việc gửi thật do DeliveryWorker làm trong
 * hạn mức của từng kênh. Chạy lại một trang không tạo trùng nhờ dedupKey theo (chiến dịch, người nhận).
 */
@Injectable()
export class BroadcastWorker implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger(BroadcastWorker.name);
  private timer?: NodeJS.Timeout;
  private running?: Promise<void>;
  private stopping = false;

  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(READ_REPOSITORIES) private readonly read: ReadRepositories,
    @Inject(RECIPIENT_DIRECTORY) private readonly directory: RecipientDirectory,
    private readonly notifications: NotificationService,
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(ID_GENERATOR) private readonly ids: IdGenerator,
    @Inject(BROADCAST_WORKER_OPTIONS) private readonly opts: BroadcastWorkerOptions,
  ) {}

  onApplicationBootstrap() {
    if (!this.opts.enabled) return;
    this.timer = setInterval(() => {
      this.running ??= this.tick()
        .catch((e: Error) => this.log.error(`broadcast tick failed: ${e.message}`))
        .finally(() => (this.running = undefined));
    }, this.opts.intervalMs);
  }

  async onApplicationShutdown() {
    this.stopping = true;
    clearInterval(this.timer);
    await this.running;
  }

  /** Một vòng: đóng các chiến dịch đã gửi xong, rồi chia trang một chiến dịch đến hạn (public để test). */
  async tick(): Promise<void> {
    const now = this.clock.now();
    for (const id of await this.read.broadcasts.completeFinished(now)) {
      this.log.log(`broadcast ${id} completed`);
    }
    const broadcast = await this.read.broadcasts.claimDue(now, this.leaseFrom(now));
    if (broadcast) await this.expand(broadcast);
  }

  private async expand(b: Broadcast) {
    let cursor = b.audienceCursor;
    while (!this.stopping) {
      // Gọi Keycloak ngoài transaction để không giữ kết nối DB trong lúc chờ mạng.
      const page = await this.directory.page(b.audience, cursor, this.opts.pageSize);
      const last = page.length < this.opts.pageSize;
      const created = await this.uow.run(async (tx) => {
        const current = await tx.broadcasts.lock(b.id);
        // Đã huỷ, hoặc lease hết hạn và pod khác đã đi tiếp: dừng, không ghi đè tiến độ của họ.
        if (current?.status !== 'EXPANDING' || current.audienceCursor !== cursor) return undefined;
        const notifications = await this.enqueuePage(
          tx,
          b,
          page.filter((r) => r.enabled),
        );
        await tx.broadcasts.advance(b.id, cursor + page.length, notifications.length, this.leaseFrom(this.clock.now()));
        if (last) await tx.broadcasts.markSending(b.id);
        return notifications;
      });
      if (!created) {
        this.log.log(`broadcast ${b.id} stopped expanding at ${cursor} (cancelled or taken over)`);
        return;
      }
      await this.notifications.publish(created);
      cursor += page.length;
      if (last) {
        this.log.log(`broadcast ${b.id} queued for ${cursor} users`);
        return;
      }
    }
  }

  private async enqueuePage(
    tx: TransactionScope,
    b: Broadcast,
    recipients: readonly Recipient[],
  ): Promise<Notification[]> {
    const now = this.clock.now();
    const preferencesByUserId = await tx.preferences.getMany(recipients.map((r) => r.id));
    const items: NewNotification[] = [];
    for (const r of recipients) {
      const preferences = preferencesByUserId.get(r.id);
      if (!preferences) continue;
      const channels = channelsFor(preferences, b.category, b.channels, now);
      if (channels.length === 0) continue;
      items.push({
        notification: {
          id: this.ids.next(),
          userId: r.id,
          category: b.category,
          type: 'BROADCAST',
          title: b.title,
          body: b.body,
          link: b.link,
          dedupKey: broadcastDedupKey(b.id, r.id),
          readAt: null,
          createdAt: now,
        },
        channels: channels.filter((c) => c !== 'IN_APP'),
      });
    }
    return tx.notifications.insertManyIfNew(items, now, { priority: DELIVERY_PRIORITY.BULK, broadcastId: b.id });
  }

  private leaseFrom(now: Date) {
    return new Date(now.getTime() + LEASE_MS);
  }
}
