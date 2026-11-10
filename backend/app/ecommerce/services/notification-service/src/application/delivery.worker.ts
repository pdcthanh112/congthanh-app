import { Inject, Injectable, Logger, type OnApplicationBootstrap, type OnApplicationShutdown } from '@nestjs/common';
import { type Channel, MAX_DELIVERY_ATTEMPTS, nextAttemptDelayMs } from '../domain/notification.js';
import {
  CHANNEL_SENDERS,
  type ChannelSender,
  CLOCK,
  type Clock,
  NoAddressError,
  PermanentDeliveryError,
  READ_REPOSITORIES,
  RECIPIENT_DIRECTORY,
  type ReadRepositories,
  type RecipientDirectory,
} from './ports.js';

export const WORKER_OPTIONS = Symbol('WORKER_OPTIONS');
export interface WorkerOptions {
  enabled: boolean;
  intervalMs: number;
  batchSize: number;
}

/**
 * Gửi các kênh ngoài (email/SMS/push) từ bảng deliveries: claim theo lô bằng FOR UPDATE SKIP LOCKED + lease,
 * lỗi tạm thời thì hẹn giờ thử lại (backoff), lỗi vĩnh viễn thì đánh dấu FAILED. Chạy được nhiều pod.
 */
@Injectable()
export class DeliveryWorker implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger(DeliveryWorker.name);
  private timer?: NodeJS.Timeout;
  private running?: Promise<void>;
  private readonly senders: Map<Channel, ChannelSender>;

  constructor(
    @Inject(READ_REPOSITORIES) private readonly repos: ReadRepositories,
    @Inject(RECIPIENT_DIRECTORY) private readonly directory: RecipientDirectory,
    @Inject(CHANNEL_SENDERS) senders: ChannelSender[],
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(WORKER_OPTIONS) private readonly opts: WorkerOptions,
  ) {
    this.senders = new Map(senders.map((s) => [s.channel, s]));
  }

  onApplicationBootstrap() {
    if (!this.opts.enabled) return;
    this.timer = setInterval(() => {
      this.running ??= this.tick()
        .then(
          () => undefined,
          (e: Error) => this.log.error(`delivery tick failed: ${e.message}`),
        )
        .finally(() => (this.running = undefined));
    }, this.opts.intervalMs);
  }

  async onApplicationShutdown() {
    clearInterval(this.timer);
    await this.running;
  }

  /** Một vòng: trả về số lượt đã xử lý (public để test). */
  async tick(): Promise<number> {
    const now = this.clock.now();
    const due = await this.repos.deliveries.claimDue(this.opts.batchSize, now, new Date(now.getTime() + 5 * 60_000));
    await Promise.all(due.map((d) => this.deliver(d.id, d.notificationId, d.channel, d.attempts)));
    return due.length;
  }

  private async deliver(id: string, notificationId: string, channel: Channel, attempts: number) {
    const sender = this.senders.get(channel);
    if (!sender) return this.repos.deliveries.markSkipped(id, `channel ${channel} not configured`);
    try {
      const n = await this.repos.notifications.get(notificationId);
      const recipient = n && (await this.directory.find(n.userId));
      if (!n || !recipient?.enabled) return this.repos.deliveries.markSkipped(id, 'recipient not found or disabled');
      await sender.send(recipient, { title: n.title, body: n.body, link: n.link }, this.repos.devices);
      await this.repos.deliveries.markSent(id);
    } catch (e) {
      const err = e as Error;
      const permanent = err instanceof PermanentDeliveryError;
      const retry = !permanent && attempts + 1 < MAX_DELIVERY_ATTEMPTS;
      const retryAt = retry ? new Date(this.clock.now().getTime() + nextAttemptDelayMs(attempts + 1)) : null;
      if (err instanceof NoAddressError) return this.repos.deliveries.markSkipped(id, err.message);
      this.log.warn(`${channel} delivery ${id} failed (attempt ${attempts + 1}): ${err.message}`);
      await this.repos.deliveries.markFailed(id, err.message.slice(0, 500), retryAt);
    }
  }
}
