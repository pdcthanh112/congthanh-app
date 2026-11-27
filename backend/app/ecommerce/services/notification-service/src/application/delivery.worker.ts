import { Inject, Injectable, Logger, type OnApplicationBootstrap, type OnApplicationShutdown } from '@nestjs/common';
import {
  CHANNELS,
  type Channel,
  MAX_DELIVERY_ATTEMPTS,
  nextAttemptDelayMs,
  TRANSACTIONAL,
} from '../domain/notification.js';
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
  SEND_RATE_LIMITER,
  type SendRateLimiter,
} from './ports.js';

export const WORKER_OPTIONS = Symbol('WORKER_OPTIONS');
export interface WorkerOptions {
  enabled: boolean;
  intervalMs: number;
  /** Số lượt tối đa mỗi kênh trong một vòng. */
  batchSize: number;
}

/** Kênh ngoài đi qua hàng đợi; IN_APP lưu thẳng khi tạo thông báo. */
const QUEUED_CHANNELS: readonly Channel[] = CHANNELS.filter((c) => c !== 'IN_APP');
/** Giữ lượt đã claim tới lúc này; quá hạn mà chưa đánh dấu thì pod khác lấy lại. */
const LEASE_MS = 5 * 60_000;

interface TickResult {
  processed: number;
  /** Có kênh bị hạn mức cắt bớt: còn việc nhưng phải chờ cửa sổ sau. */
  throttled: boolean;
}

/**
 * Gửi các kênh ngoài (email/SMS/push) từ bảng deliveries: mỗi kênh claim một lô theo ưu tiên bằng
 * FOR UPDATE SKIP LOCKED + lease, trong hạn mức gửi của kênh đó (ADR 0034). Lỗi tạm thời thì hẹn giờ thử lại
 * (backoff), lỗi vĩnh viễn thì FAILED. Còn việc thì chạy tiếp ngay thay vì chờ vòng sau. Chạy được nhiều pod.
 */
@Injectable()
export class DeliveryWorker implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger(DeliveryWorker.name);
  private timer?: NodeJS.Timeout;
  private running?: Promise<void>;
  private stopping = false;
  private readonly senders: Map<Channel, ChannelSender>;

  constructor(
    @Inject(READ_REPOSITORIES) private readonly repos: ReadRepositories,
    @Inject(RECIPIENT_DIRECTORY) private readonly directory: RecipientDirectory,
    @Inject(CHANNEL_SENDERS) senders: ChannelSender[],
    @Inject(SEND_RATE_LIMITER) private readonly limiter: SendRateLimiter,
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(WORKER_OPTIONS) private readonly opts: WorkerOptions,
  ) {
    this.senders = new Map(senders.map((s) => [s.channel, s]));
  }

  onApplicationBootstrap() {
    if (!this.opts.enabled) return;
    this.timer = setInterval(() => {
      this.running ??= this.drain()
        .catch((e: Error) => this.log.error(`delivery tick failed: ${e.message}`))
        .finally(() => (this.running = undefined));
    }, this.opts.intervalMs);
  }

  async onApplicationShutdown() {
    this.stopping = true;
    clearInterval(this.timer);
    await this.running;
  }

  /** Một vòng: trả về số lượt đã xử lý (public để test). */
  async tick(): Promise<number> {
    return (await this.tickChannels()).processed;
  }

  /** Chạy liên tục khi còn việc; bị hạn mức thì đợi sang giây kế tiếp, hết việc thì nghỉ tới vòng sau. */
  private async drain() {
    while (!this.stopping) {
      const r = await this.tickChannels();
      if (r.processed === 0) return;
      if (r.throttled) await sleep(1_000 - (this.clock.now().getTime() % 1_000));
    }
  }

  private async tickChannels(): Promise<TickResult> {
    const now = this.clock.now();
    const lease = new Date(now.getTime() + LEASE_MS);
    const results = await Promise.all(QUEUED_CHANNELS.map((c) => this.tickChannel(c, now, lease)));
    return {
      processed: results.reduce((sum, r) => sum + r.processed, 0),
      throttled: results.some((r) => r.throttled),
    };
  }

  private async tickChannel(channel: Channel, now: Date, lease: Date): Promise<TickResult> {
    const sender = this.senders.get(channel);
    // Kênh chưa cấu hình không tốn hạn mức: claim để đánh dấu SKIPPED.
    const allowed = sender ? await this.limiter.acquire(channel, this.opts.batchSize) : this.opts.batchSize;
    if (allowed === 0) return { processed: 0, throttled: true };
    const due = await this.repos.deliveries.claimDue(channel, allowed, now, lease);
    if (sender && due.length < allowed) await this.limiter.release(channel, allowed - due.length);
    await Promise.all(due.map((d) => this.deliver(d.id, d.notificationId, channel, d.attempts)));
    return { processed: due.length, throttled: allowed < this.opts.batchSize && due.length === allowed };
  }

  private async deliver(id: string, notificationId: string, channel: Channel, attempts: number) {
    const sender = this.senders.get(channel);
    if (!sender) return this.repos.deliveries.markSkipped(id, `channel ${channel} not configured`);
    try {
      const n = await this.repos.notifications.get(notificationId);
      const recipient = n && (await this.directory.find(n.userId));
      if (!n || !recipient?.enabled) return this.repos.deliveries.markSkipped(id, 'recipient not found or disabled');
      const marketing = !TRANSACTIONAL.has(n.category);
      await sender.send(recipient, { title: n.title, body: n.body, link: n.link, marketing }, this.repos.devices);
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

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));
