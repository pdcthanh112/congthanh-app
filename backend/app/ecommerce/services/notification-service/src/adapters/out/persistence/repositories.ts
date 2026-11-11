import type { Database } from '@ecommerce/platform';
import { and, count, desc, eq, inArray, isNull, sql } from 'drizzle-orm';
import { v7 as uuidv7 } from 'uuid';
import type {
  DeliveryQueue,
  DeviceRepository,
  LocalViews,
  NotificationRepository,
  PreferenceRepository,
  WebPushSubscription,
} from '../../../application/ports.js';
import type { Category, Channel, Delivery, Notification } from '../../../domain/notification.js';
import { defaultPreferences, type Preferences } from '../../../domain/preferences.js';
import type * as schema from './schema.js';
import {
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

  async insertIfNew(n: Notification, channels: readonly Channel[], now: Date) {
    const rows = await this.db.insert(notifications).values(n).onConflictDoNothing().returning();
    const created = rows[0];
    if (!created) return undefined;
    if (channels.length > 0) {
      await this.db
        .insert(deliveries)
        .values(channels.map((channel) => ({ id: uuidv7(), notificationId: created.id, channel, nextAttemptAt: now })));
    }
    return toNotification(created);
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

  async claimDue(limit: number, now: Date, lease: Date): Promise<Delivery[]> {
    // Claim trong một câu lệnh: chọn bằng SKIP LOCKED rồi đẩy next_attempt_at ra sau (lease) để pod khác không lấy.
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
        where status = 'PENDING' and next_attempt_at <= ${now}
        order by next_attempt_at
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
