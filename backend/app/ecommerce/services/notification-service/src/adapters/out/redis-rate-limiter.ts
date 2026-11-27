import { Logger, type OnApplicationShutdown } from '@nestjs/common';
import { Redis } from 'ioredis';
import type { SendRateLimiter } from '../../application/ports.js';
import type { Channel } from '../../domain/notification.js';

/** Hạn mức mỗi giây theo kênh; kênh không có trong map thì không giới hạn. */
export type RatesPerSecond = Partial<Record<Channel, number>>;

const WINDOW_MS = 1_000;

// Cấp tối đa ARGV[1] lượt trong cửa sổ còn (ARGV[2] - đã dùng); nguyên tử nên nhiều pod không vượt hạn mức.
const ACQUIRE = `
local used = tonumber(redis.call('GET', KEYS[1]) or '0')
local grant = math.min(tonumber(ARGV[1]), math.max(tonumber(ARGV[2]) - used, 0))
if grant > 0 then
  redis.call('INCRBY', KEYS[1], grant)
  redis.call('PEXPIRE', KEYS[1], ARGV[3])
end
return grant`;

// Chỉ trả lại khi cửa sổ còn sống; DECRBY lên khoá đã hết hạn sẽ tạo khoá âm không có TTL.
const RELEASE = `
if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('DECRBY', KEYS[1], ARGV[1]) end
return 0`;

/**
 * Hạn mức gửi theo cửa sổ cố định 1 giây trong Redis, dùng chung mọi pod (ADR 0034).
 * Redis lỗi thì cho qua toàn bộ lượt xin: hạn mức chỉ để tránh bị nhà cung cấp chặn, không phải kiểm soát bảo mật,
 * và email giao dịch không được dừng chỉ vì Redis; nhà cung cấp trả 429 thì worker vẫn thử lại theo backoff.
 */
export class RedisSendRateLimiter implements SendRateLimiter, OnApplicationShutdown {
  private readonly log = new Logger(RedisSendRateLimiter.name);
  private readonly client: Redis;
  private readonly windowKeyByChannel = new Map<Channel, string>();

  constructor(
    url: string,
    private readonly rates: RatesPerSecond,
  ) {
    this.client = new Redis(url, { lazyConnect: true, maxRetriesPerRequest: 1 });
    this.client.on('error', (e: Error) => this.log.warn(`redis: ${e.message}`));
  }

  async acquire(channel: Channel, wanted: number): Promise<number> {
    const limit = this.rates[channel];
    if (limit === undefined || wanted <= 0) return wanted;
    const key = `notification:send-rate:${channel}:${Math.floor(Date.now() / WINDOW_MS)}`;
    this.windowKeyByChannel.set(channel, key);
    try {
      const granted = await this.client.eval(ACQUIRE, 1, key, wanted, limit, WINDOW_MS * 2);
      return Number(granted);
    } catch (e) {
      this.log.warn(`rate limiter unavailable, allowing ${wanted} ${channel} sends: ${(e as Error).message}`);
      return wanted;
    }
  }

  async release(channel: Channel, unused: number): Promise<void> {
    const key = this.windowKeyByChannel.get(channel);
    if (this.rates[channel] === undefined || !key || unused <= 0) return;
    try {
      await this.client.eval(RELEASE, 1, key, unused);
    } catch {
      // Không trả lại được thì cửa sổ này chỉ gửi ít hơn hạn mức; cửa sổ sau tự reset.
    }
  }

  async onApplicationShutdown() {
    await this.client.quit().catch(() => undefined);
  }
}
