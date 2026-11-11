import type { RedisPubSub } from '@ecommerce/platform';
import type { RealtimePublisher } from '../../application/ports.js';
import type { Notification } from '../../domain/notification.js';

export const userChannel = (userId: string) => `notification:user:${userId}`;

/** JSON qua Redis làm mất kiểu Date: dựng lại khi nhận. */
const revive = (n: Notification): Notification => ({
  ...n,
  createdAt: new Date(n.createdAt),
  readAt: n.readAt ? new Date(n.readAt) : null,
});

export class RedisRealtimePublisher implements RealtimePublisher {
  constructor(private readonly pubsub: RedisPubSub) {}

  notificationCreated(n: Notification) {
    return this.pubsub.publish(userChannel(n.userId), n);
  }

  stream(userId: string) {
    return this.pubsub.subscribe<Notification>(userChannel(userId), revive);
  }
}
