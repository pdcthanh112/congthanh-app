import type { RedisPubSub } from '@ecommerce/platform';
import { Redis } from 'ioredis';
import type { RateLimiter, Realtime, RealtimeEvent } from '../../application/ports.js';

export const userChannel = (userId: string) => `chat:user:${userId}`;
export const SUPPORT_CHANNEL = 'chat:support';

export class RedisRealtime implements Realtime {
  constructor(private readonly pubsub: RedisPubSub) {}

  async publish(userIds: readonly string[], toSupport: boolean, event: RealtimeEvent) {
    await Promise.all([
      ...userIds.map((id) => this.pubsub.publish(userChannel(id), event)),
      ...(toSupport ? [this.pubsub.publish(SUPPORT_CHANNEL, event)] : []),
    ]);
  }

  stream(userId: string, includeSupport: boolean) {
    const own = this.pubsub.subscribe<RealtimeEvent>(userChannel(userId));
    return includeSupport ? merge([own, this.pubsub.subscribe<RealtimeEvent>(SUPPORT_CHANNEL)]) : own;
  }
}

/** Gộp nhiều async iterator (kênh cá nhân + kênh hỗ trợ) thành một; return() huỷ tất cả. */
function merge<T>(sources: AsyncIterableIterator<T>[]): AsyncIterableIterator<T> {
  const pending = new Map(sources.map((s, i) => [i, s.next().then((r) => ({ i, r }))]));
  return {
    async next() {
      while (pending.size > 0) {
        const { i, r } = await Promise.race(pending.values());
        if (r.done) {
          pending.delete(i);
          continue;
        }
        pending.set(
          i,
          sources[i]!.next().then((x) => ({ i, r: x })),
        );
        return r;
      }
      return { value: undefined, done: true };
    },
    async return() {
      await Promise.all(sources.map((s) => s.return?.()));
      return { value: undefined, done: true };
    },
    [Symbol.asyncIterator]() {
      return this;
    },
  };
}

/** Giới hạn tần suất cửa sổ cố định bằng INCR + EXPIRE (đủ cho chống spam chat). Redis lỗi thì cho qua. */
export class RedisRateLimiter implements RateLimiter {
  private readonly client: Redis;

  constructor(url: string) {
    this.client = new Redis(url, { lazyConnect: true, maxRetriesPerRequest: 1 });
    this.client.on('error', () => undefined);
  }

  async allow(key: string, limit: number, windowSeconds: number) {
    try {
      const bucket = `ratelimit:${key}:${Math.floor(Date.now() / 1000 / windowSeconds)}`;
      const [[, count]] = (await this.client.multi().incr(bucket).expire(bucket, windowSeconds).exec()) as [
        [unknown, number],
      ];
      return count <= limit;
    } catch {
      return true;
    }
  }
}
