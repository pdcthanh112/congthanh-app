import type { Database } from '@ecommerce/platform';
import { nextSnowflakeId } from '@ecommerce/platform';
import { and, count, desc, eq, inArray, isNull, lt, lte, or, sql } from 'drizzle-orm';
import type {
  BroadcastRepository,
  DeliveryCount,
  DeliveryQueue,
  DeviceRepository,
  EnqueueOptions,
  LocalViews,
  NewNotification,
  NotificationRepository,
  PreferenceRepository,
  WebPushSubscription,
} from '../../../application/ports.js';
import type { Broadcast } from '../../../domain/broadcast.js';
import type { Category, Channel, Delivery, Notification } from '../../../domain/notification.js';
import { defaultPreferences, type Preferences } from '../../../domain/preferences.js';
import type * as schema from './schema.js';
import {
  broadcasts,
  deliveries,
  fcmTokens,
  notifications,
  orderCustomers,
  preferences,
  productStates,
  productWatchers,
  webPushSubscriptions,
} from './schema.js';

export type Conn = Pick<Database<typeof schema>, 'select' | 'insert' | 'update' | 'delete' | 'execute'>;

const toNotification = (r: typeof notifications.$inferSelect): Notification => ({
  ...r,
  category: r.category as Category,
});

export class DrizzleNotificationRepository implements NotificationRepository {
  constructor(private readonly db: Conn) {}

  async insertIfNew(n: Notification, channels: readonly Channel[], now: Date, opts: EnqueueOptions) {
    const [created] = await this.insertManyIfNew([{ notification: n, channels }], now, opts);
    return created;
  }

  async insertManyIfNew(items: readonly NewNotification[], now: Date, opts: EnqueueOptions) {
    if (items.length === 0) return [];
    const rows = await this.db
      .insert(notifications)
      .values(items.map((i) => i.notification))
      .onConflictDoNothing({ target: notifications.dedupKey })
      .returning();
    const channelsByDedupKey = new Map(items.map((i) => [i.notification.dedupKey, i.channels]));
    const queued = rows.flatMap((r) =>
      (channelsByDedupKey.get(r.dedupKey) ?? []).map((channel) => ({
        id: nextSnowflakeId(),
        notificationId: r.id,
        channel,
        nextAttemptAt: now,
        priority: opts.priority,
        broadcastId: opts.broadcastId ?? null,
      })),
    );
    if (queued.length > 0) await this.db.insert(deliveries).values(queued);
    return rows.map(toNotification);
  }

  async list(userId: string, unreadOnly: boolean, limit: number, offset: number) {
    const cond = unreadOnly
      ? and(eq(notifications.userId, userId), isNull(notifications.readAt))
      : eq(notifications.userId, userId);
    const [rows, [total]] = await Promise.all([
      this.db
        .select()
        .from(notifications)
        .where(cond)
        .orderBy(desc(notifications.createdAt))
        .limit(limit)
        .offset(offset),
      this.db.select({ n: count() }).from(notifications).where(cond),
    ]);
    return { items: rows.map(toNotification), total: total?.n ?? 0 };
  }

  async unreadCount(userId: string) {
    const [row] = await this.db
      .select({ n: count() })
      .from(notifications)
      .where(and(eq(notifications.userId, userId), isNull(notifications.readAt)));
    return row?.n ?? 0;
  }

  async markRead(userId: string, ids: readonly string[] | 'ALL', at: Date) {
    const base = and(eq(notifications.userId, userId), isNull(notifications.readAt));
    const cond = ids === 'ALL' ? base : and(base, inArray(notifications.id, [...ids]));
    if (ids !== 'ALL' && ids.length === 0) return 0;
    const rows = await this.db
      .update(notifications)
      .set({ readAt: at })
      .where(cond)
      .returning({ id: notifications.id });
    return rows.length;
  }

  async get(id: string) {
    const [row] = await this.db.select().from(notifications).where(eq(notifications.id, id));
    return row && toNotification(row);
  }
}

export class DrizzlePreferenceRepository implements PreferenceRepository {
  constructor(private readonly db: Conn) {}

  async get(userId: string): Promise<Preferences> {
    const [row] = await this.db.select().from(preferences).where(eq(preferences.userId, userId));
    if (!row) return defaultPreferences(userId);
    return { userId, disabled: new Set(row.disabled), quietHours: row.quietHours ?? null };
  }

  async getMany(userIds: readonly string[]) {
    const rows =
      userIds.length === 0
        ? []
        : await this.db
            .select()
            .from(preferences)
            .where(inArray(preferences.userId, [...userIds]));
    const byUserId = new Map<string, Preferences>(
      rows.map((r) => [
        r.userId,
        { userId: r.userId, disabled: new Set(r.disabled), quietHours: r.quietHours ?? null },
      ]),
    );
    for (const id of userIds) {
      if (!byUserId.has(id)) byUserId.set(id, defaultPreferences(id));
    }
    return byUserId;
  }

  async save(p: Preferences) {
    const values = {
      userId: p.userId,
      disabled: [...p.disabled].sort(),
      quietHours: p.quietHours,
      updatedAt: new Date(),
    };
    await this.db.insert(preferences).values(values).onConflictDoUpdate({ target: preferences.userId, set: values });
  }
}

export class DrizzleDeviceRepository implements DeviceRepository {
  constructor(private readonly db: Conn) {}

  async addWebPush(userId: string, s: WebPushSubscription) {
    await this.db
      .insert(webPushSubscriptions)
      .values({ ...s, userId })
      .onConflictDoUpdate({ target: webPushSubscriptions.endpoint, set: { userId, p256dh: s.p256dh, auth: s.auth } });
  }
  async removeWebPush(endpoint: string) {
    await this.db.delete(webPushSubscriptions).where(eq(webPushSubscriptions.endpoint, endpoint));
  }
  webPushOf(userId: string) {
    return this.db
      .select({
        endpoint: webPushSubscriptions.endpoint,
        p256dh: webPushSubscriptions.p256dh,
        auth: webPushSubscriptions.auth,
      })
      .from(webPushSubscriptions)
      .where(eq(webPushSubscriptions.userId, userId));
  }
  async addFcmToken(userId: string, token: string, platform: string) {
    await this.db
      .insert(fcmTokens)
      .values({ token, userId, platform })
      .onConflictDoUpdate({ target: fcmTokens.token, set: { userId, platform } });
  }
  async removeFcmToken(token: string) {
    await this.db.delete(fcmTokens).where(eq(fcmTokens.token, token));
  }
  async fcmTokensOf(userId: string) {
    const rows = await this.db.select({ token: fcmTokens.token }).from(fcmTokens).where(eq(fcmTokens.userId, userId));
    return rows.map((r) => r.token);
  }
}

export class DrizzleDeliveryQueue implements DeliveryQueue {
  constructor(private readonly db: Database<typeof schema>) {}

  async claimDue(channel: Channel, limit: number, now: Date, lease: Date): Promise<Delivery[]> {
    // Claim trong một câu lệnh: chọn bằng SKIP LOCKED rồi đẩy next_attempt_at ra sau (lease) để pod khác không lấy.
    // Theo từng kênh (mỗi kênh một hạn mức) và ưu tiên trước, rồi mới tới hạn sớm nhất (ADR 0034).
    const rows = await this.db.execute<{
      id: string;
      notification_id: string;
      channel: Channel;
      status: Delivery['status'];
      attempts: number;
      last_error: string | null;
      next_attempt_at: Date;
    }>(sql`
      update ${deliveries} d set next_attempt_at = ${lease}
      from (
        select id from ${deliveries}
        where status = 'PENDING' and channel = ${channel} and next_attempt_at <= ${now}
        order by priority, next_attempt_at
        limit ${limit}
        for update skip locked
      ) due
      where d.id = due.id
      returning d.id, d.notification_id, d.channel, d.status, d.attempts, d.last_error, d.next_attempt_at`);
    return rows.rows.map((r) => ({
      id: r.id,
      notificationId: r.notification_id,
      channel: r.channel,
      status: r.status,
      attempts: r.attempts,
      lastError: r.last_error,
      nextAttemptAt: new Date(r.next_attempt_at),
    }));
  }

  async markSent(id: string) {
    await this.db
      .update(deliveries)
      .set({ status: 'SENT', sentAt: new Date(), attempts: sql`${deliveries.attempts} + 1` })
      .where(eq(deliveries.id, id));
  }

  async markSkipped(id: string, reason: string) {
    await this.db.update(deliveries).set({ status: 'SKIPPED', lastError: reason }).where(eq(deliveries.id, id));
  }

  async markFailed(id: string, error: string, retryAt: Date | null) {
    await this.db
      .update(deliveries)
      .set({
        status: retryAt ? 'PENDING' : 'FAILED',
        lastError: error,
        attempts: sql`${deliveries.attempts} + 1`,
        ...(retryAt ? { nextAttemptAt: retryAt } : {}),
      })
      .where(eq(deliveries.id, id));
  }
}

export class DrizzleLocalViews implements LocalViews {
  constructor(private readonly db: Conn) {}

  async rememberOrder(orderId: string, customerId: string) {
    await this.db.insert(orderCustomers).values({ orderId, customerId }).onConflictDoNothing();
  }
  async customerOfOrder(orderId: string) {
    const [row] = await this.db.select().from(orderCustomers).where(eq(orderCustomers.orderId, orderId));
    return row?.customerId;
  }
  async watch(customerId: string, productId: string) {
    await this.db.insert(productWatchers).values({ customerId, productId }).onConflictDoNothing();
  }
  async unwatch(customerId: string, productId: string) {
    await this.db
      .delete(productWatchers)
      .where(and(eq(productWatchers.customerId, customerId), eq(productWatchers.productId, productId)));
  }
  async watchers(productId: string) {
    const rows = await this.db
      .select({ id: productWatchers.customerId })
      .from(productWatchers)
      .where(eq(productWatchers.productId, productId));
    return rows.map((r) => r.id);
  }
  async updateProduct(
    productId: string,
    patch: { name?: string; priceMinor?: number; currency?: string; available?: number },
  ) {
    const [before] = await this.db
      .select()
      .from(productStates)
      .where(eq(productStates.productId, productId))
      .for('update');
    await this.db
      .insert(productStates)
      .values({ productId, ...patch })
      .onConflictDoUpdate({ target: productStates.productId, set: { ...patch, updatedAt: new Date() } });
    return (
      before && {
        name: before.name,
        priceMinor: before.priceMinor,
        currency: before.currency,
        available: before.available,
      }
    );
  }
}

const toBroadcast = (r: typeof broadcasts.$inferSelect): Broadcast => ({
  id: r.id,
  category: r.category as Category,
  title: r.title,
  body: r.body,
  link: r.link,
  channels: r.channels,
  audience: r.audience,
  status: r.status,
  scheduledAt: r.scheduledAt,
  audienceCursor: r.audienceCursor,
  recipients: r.recipients,
  createdBy: r.createdBy,
  createdAt: r.createdAt,
  completedAt: r.completedAt,
  cancelledAt: r.cancelledAt,
});

export class DrizzleBroadcastRepository implements BroadcastRepository {
  constructor(private readonly db: Conn) {}

  async insertOrGet(b: Broadcast, idempotencyKey: string) {
    const [row] = await this.db
      .insert(broadcasts)
      .values({ ...b, idempotencyKey })
      .onConflictDoNothing({ target: [broadcasts.createdBy, broadcasts.idempotencyKey] })
      .returning();
    if (row) return toBroadcast(row);
    const [existing] = await this.db
      .select()
      .from(broadcasts)
      .where(and(eq(broadcasts.createdBy, b.createdBy), eq(broadcasts.idempotencyKey, idempotencyKey)));
    if (!existing) throw new Error(`broadcast with idempotency key ${idempotencyKey} vanished`);
    return toBroadcast(existing);
  }

  async get(id: string) {
    const [row] = await this.db.select().from(broadcasts).where(eq(broadcasts.id, id));
    return row && toBroadcast(row);
  }

  async lock(id: string) {
    const [row] = await this.db.select().from(broadcasts).where(eq(broadcasts.id, id)).for('update');
    return row && toBroadcast(row);
  }

  async list(limit: number, offset: number) {
    const [rows, [total]] = await Promise.all([
      this.db.select().from(broadcasts).orderBy(desc(broadcasts.createdAt)).limit(limit).offset(offset),
      this.db.select({ n: count() }).from(broadcasts),
    ]);
    return { items: rows.map(toBroadcast), total: total?.n ?? 0 };
  }

  async claimDue(now: Date, lease: Date) {
    const due = this.db
      .select({ id: broadcasts.id })
      .from(broadcasts)
      .where(
        and(
          inArray(broadcasts.status, ['SCHEDULED', 'EXPANDING']),
          lte(broadcasts.scheduledAt, now),
          or(isNull(broadcasts.leaseUntil), lt(broadcasts.leaseUntil, now)),
        ),
      )
      .orderBy(broadcasts.scheduledAt)
      .limit(1)
      .for('update', { skipLocked: true });
    const [row] = await this.db
      .update(broadcasts)
      .set({ status: 'EXPANDING', leaseUntil: lease })
      .where(inArray(broadcasts.id, due))
      .returning();
    return row && toBroadcast(row);
  }

  async advance(id: string, cursor: number, recipientsAdded: number, lease: Date) {
    await this.db
      .update(broadcasts)
      .set({
        audienceCursor: cursor,
        recipients: sql`${broadcasts.recipients} + ${recipientsAdded}`,
        leaseUntil: lease,
      })
      .where(eq(broadcasts.id, id));
  }

  async markSending(id: string) {
    await this.db.update(broadcasts).set({ status: 'SENDING', leaseUntil: null }).where(eq(broadcasts.id, id));
  }

  async cancel(id: string, at: Date) {
    await this.db
      .update(broadcasts)
      .set({ status: 'CANCELLED', cancelledAt: at, leaseUntil: null })
      .where(eq(broadcasts.id, id));
    const skipped = await this.db
      .update(deliveries)
      .set({ status: 'SKIPPED', lastError: 'broadcast cancelled' })
      .where(and(eq(deliveries.broadcastId, id), eq(deliveries.status, 'PENDING')))
      .returning({ id: deliveries.id });
    return skipped.length;
  }

  async completeFinished(at: Date) {
    const rows = await this.db
      .update(broadcasts)
      .set({ status: 'COMPLETED', completedAt: at })
      .where(
        and(
          eq(broadcasts.status, 'SENDING'),
          sql`not exists (select 1 from ${deliveries} d where d.broadcast_id = ${broadcasts.id} and d.status = 'PENDING')`,
        ),
      )
      .returning({ id: broadcasts.id });
    return rows.map((r) => r.id);
  }

  async deliveryCounts(id: string): Promise<DeliveryCount[]> {
    return this.db
      .select({ channel: deliveries.channel, status: deliveries.status, count: count() })
      .from(deliveries)
      .where(eq(deliveries.broadcastId, id))
      .groupBy(deliveries.channel, deliveries.status);
  }
}
