import { Logger, type OnApplicationShutdown } from '@nestjs/common';
import { Redis } from 'ioredis';

type Listener = (message: string) => void;

/**
 * Pub/sub qua Redis cho GraphQL subscription chạy nhiều pod: pod nào nhận mutation cũng publish,
 * pod nào đang giữ kết nối WebSocket của người nhận cũng nhận được. Một kết nối subscriber dùng chung,
 * đếm số listener theo kênh để SUBSCRIBE/UNSUBSCRIBE đúng lúc.
 */
export class RedisPubSub implements OnApplicationShutdown {
  private readonly log = new Logger(RedisPubSub.name);
  private readonly pub: Redis;
  private readonly sub: Redis;
  private readonly listeners = new Map<string, Set<Listener>>();

  constructor(url: string) {
    this.pub = new Redis(url, { lazyConnect: true, maxRetriesPerRequest: 3 });
    this.sub = new Redis(url, { lazyConnect: true });
    this.sub.on('message', (channel: string, message: string) => {
      for (const l of this.listeners.get(channel) ?? []) l(message);
    });
    for (const c of [this.pub, this.sub]) c.on('error', (e: Error) => this.log.warn(`redis: ${e.message}`));
  }

  async publish(channel: string, payload: unknown): Promise<void> {
    await this.pub.publish(channel, JSON.stringify(payload));
  }

  /** Async iterator cho resolver @Subscription; client ngắt kết nối thì graphql gọi return() → tự huỷ đăng ký. */
  subscribe<T>(channel: string, revive?: (raw: T) => T): AsyncIterableIterator<T> {
    const queue: T[] = [];
    let wake: (() => void) | undefined;
    let done = false;
    const listener: Listener = (m) => {
      const value = JSON.parse(m) as T;
      queue.push(revive ? revive(value) : value);
      wake?.();
    };
    const ready = this.addListener(channel, listener);
    const cleanup = async () => {
      done = true;
      wake?.();
      await this.removeListener(channel, listener);
    };
    const iterator: AsyncIterableIterator<T> = {
      async next() {
        await ready;
        while (!done && queue.length === 0) await new Promise<void>((r) => (wake = r));
        if (done) return { value: undefined, done: true };
        return { value: queue.shift() as T, done: false };
      },
      async return() {
        await cleanup();
        return { value: undefined, done: true };
      },
      async throw(e?: unknown) {
        await cleanup();
        throw e;
      },
      [Symbol.asyncIterator]() {
        return this;
      },
    };
    return iterator;
  }

  private async addListener(channel: string, l: Listener) {
    let set = this.listeners.get(channel);
    if (!set) {
      set = new Set();
      this.listeners.set(channel, set);
      if (this.sub.status === 'wait') await this.sub.connect();
      await this.sub.subscribe(channel);
    }
    set.add(l);
  }

  private async removeListener(channel: string, l: Listener) {
    const set = this.listeners.get(channel);
    if (!set) return;
    set.delete(l);
    if (set.size === 0) {
      this.listeners.delete(channel);
      await this.sub.unsubscribe(channel).catch(() => undefined);
    }
  }

  async ping(): Promise<boolean> {
    if (this.pub.status === 'wait') await this.pub.connect();
    return (await this.pub.ping()) === 'PONG';
  }

  async onApplicationShutdown() {
    this.pub.disconnect();
    this.sub.disconnect();
  }
}
