import { describe, expect, it } from 'vitest';
import type { Channel, Delivery, Notification } from '../domain/notification.js';
import { DeliveryWorker } from './delivery.worker.js';
import type {
  BroadcastRepository,
  ChannelSender,
  DeliveryQueue,
  DeviceRepository,
  NotificationRepository,
  PreferenceRepository,
  ReadRepositories,
  SendRateLimiter,
} from './ports.js';

const now = new Date('2026-12-01T00:00:00Z');

/** Hàng đợi giả: mỗi kênh một danh sách lượt chờ, ghi lại lượt đã gửi. */
function fakeQueue(pendingByChannel: Partial<Record<Channel, number>>) {
  const pending = new Map<Channel, Delivery[]>();
  for (const [channel, n] of Object.entries(pendingByChannel) as [Channel, number][]) {
    pending.set(
      channel,
      Array.from({ length: n }, (_, i) => ({
        id: `${channel}-${i}`,
        notificationId: 'n1',
        channel,
        status: 'PENDING',
        attempts: 0,
        lastError: null,
        nextAttemptAt: now,
      })),
    );
  }
  const sentIds: string[] = [];
  const skippedIds: string[] = [];
  const queue: DeliveryQueue = {
    claimDue: async (channel, limit) => (pending.get(channel) ?? []).splice(0, limit),
    markSent: async (id) => void sentIds.push(id),
    markSkipped: async (id) => void skippedIds.push(id),
    markFailed: async () => undefined,
  };
  return { queue, sentIds, skippedIds };
}

/** Hạn mức giả: số lượt còn lại theo kênh, ghi lại lượt trả lại. */
function fakeLimiter(remainingByChannel: Partial<Record<Channel, number>>) {
  const released: [Channel, number][] = [];
  const limiter: SendRateLimiter = {
    acquire: async (channel, wanted) => {
      const left = remainingByChannel[channel] ?? wanted;
      const granted = Math.min(left, wanted);
      remainingByChannel[channel] = left - granted;
      return granted;
    },
    release: async (channel, unused) => void released.push([channel, unused]),
  };
  return { limiter, released };
}

const notification: Notification = {
  id: 'n1',
  userId: 'u1',
  category: 'PROMOTION',
  type: 'BROADCAST',
  title: 'Flash sale',
  body: 'Giảm 50%',
  link: null,
  dedupKey: 'bc:1:u1',
  readAt: null,
  createdAt: now,
};

/** Port giả chỉ có các method truyền vào; gọi method khác thì test hỏng ngay. */
function partial<T extends object>(methods: Partial<T>): T {
  return new Proxy(methods as T, {
    get: (target, key) =>
      key in target
        ? Reflect.get(target, key)
        : () => {
            throw new Error(`${String(key)} not expected in this test`);
          },
  });
}

function worker(queue: DeliveryQueue, limiter: SendRateLimiter, senders: ChannelSender[]) {
  const repos: ReadRepositories = {
    deliveries: queue,
    notifications: partial<NotificationRepository>({ get: async () => notification }),
    preferences: partial<PreferenceRepository>({}),
    devices: partial<DeviceRepository>({}),
    broadcasts: partial<BroadcastRepository>({}),
  };
  const directory = {
    find: async () => ({ id: 'u1', email: 'u1@example.com', enabled: true }),
    withRole: async () => [],
    page: async () => [],
  };
  return new DeliveryWorker(
    repos,
    directory,
    senders,
    limiter,
    { now: () => now },
    {
      enabled: false,
      intervalMs: 0,
      batchSize: 10,
    },
  );
}

describe('DeliveryWorker theo hạn mức kênh', () => {
  it('chỉ gửi trong hạn mức của kênh, kênh khác không bị ảnh hưởng', async () => {
    const { queue, sentIds } = fakeQueue({ EMAIL: 8, FCM: 3 });
    const { limiter } = fakeLimiter({ EMAIL: 5 });
    const marketingFlags: boolean[] = [];
    const email: ChannelSender = {
      channel: 'EMAIL',
      send: async (_r, m) => void marketingFlags.push(m.marketing ?? false),
    };
    const fcm: ChannelSender = { channel: 'FCM', send: async () => undefined };

    expect(await worker(queue, limiter, [email, fcm]).tick()).toBe(8);

    expect(sentIds.filter((id) => id.startsWith('EMAIL'))).toHaveLength(5);
    expect(sentIds.filter((id) => id.startsWith('FCM'))).toHaveLength(3);
    expect(marketingFlags.every(Boolean)).toBe(true);
  });

  it('trả lại hạn mức không dùng khi hàng đợi ít hơn phần được cấp', async () => {
    const { queue } = fakeQueue({ EMAIL: 2 });
    const { limiter, released } = fakeLimiter({ EMAIL: 100 });
    const email: ChannelSender = { channel: 'EMAIL', send: async () => undefined };

    await worker(queue, limiter, [email]).tick();

    expect(released).toEqual([['EMAIL', 8]]);
  });

  it('kênh chưa cấu hình thì bỏ qua mà không tốn hạn mức', async () => {
    const { queue, skippedIds } = fakeQueue({ SMS: 2 });
    const { limiter } = fakeLimiter({ SMS: 0 });

    await worker(queue, limiter, []).tick();

    expect(skippedIds).toEqual(['SMS-0', 'SMS-1']);
  });
});
