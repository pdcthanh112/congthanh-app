import type { ValkeyPubSub } from '@ecom/platform';
import type { RealtimePublisher } from '../../application/ports.js';
import type { Notification } from '../../domain/notification.js';

export const userChannel = (userId: string) => `notification:user:${userId}`;

/** JSON qua Valkey làm mất kiểu Date: dựng lại khi nhận. */
const revive = (n: Notification): Notification => ({
  ...n,
  createdAt: new Date(n.createdAt),
  readAt: n.readAt ? new Date(n.readAt) : null,
});

export class ValkeyRealtimePublisher implements RealtimePublisher {
  constructor(private readonly pubsub: ValkeyPubSub) {}

  notificationCreated(n: Notification) {
    return this.pubsub.publish(userChannel(n.userId), n);
  }

  stream(userId: string) {
    return this.pubsub.subscribe<Notification>(userChannel(userId), revive);
  }
}
