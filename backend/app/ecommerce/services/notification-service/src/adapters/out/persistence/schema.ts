import { platformTables, snowflakeId } from "@ecommerce/platform";
import { sql } from "drizzle-orm";
import { bigint, check, index, integer, jsonb, pgSchema, primaryKey, smallint, text, timestamp, uniqueIndex, varchar } from "drizzle-orm/pg-core";

export const notificationSchema = pgSchema("notification_service");

export const channel = notificationSchema.enum("channel", ["IN_APP", "EMAIL", "SMS", "WEB_PUSH", "FCM"]);
export const deliveryStatus = notificationSchema.enum("delivery_status", ["PENDING", "SENT", "FAILED", "SKIPPED"]);

export const notifications = notificationSchema.table(
  "notifications",
  {
    id: snowflakeId().primaryKey(),
    userId: text().notNull(),
    category: varchar({ length: 20 }).notNull(),
    type: varchar({ length: 40 }).notNull(),
    title: varchar({ length: 200 }).notNull(),
    body: text().notNull(),
    link: text(),
    dedupKey: text().notNull(),
    readAt: timestamp({ withTimezone: true }),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex("notifications_dedup_key").on(t.dedupKey), index("notifications_user_recent_idx").on(t.userId, t.createdAt)],
);

export const broadcastStatus = notificationSchema.enum("broadcast_status", ["SCHEDULED", "EXPANDING", "SENDING", "COMPLETED", "CANCELLED"]);

export const broadcasts = notificationSchema.table(
  "broadcasts",
  {
    id: snowflakeId().primaryKey(),
    category: varchar({ length: 20 }).notNull(),
    title: varchar({ length: 200 }).notNull(),
    body: text().notNull(),
    link: text(),
    channels: channel().array().notNull(),
    audience: jsonb().$type<{ kind: "ALL_USERS" } | { kind: "ROLE"; role: string }>().notNull(),
    status: broadcastStatus().notNull().default("SCHEDULED"),
    scheduledAt: timestamp({ withTimezone: true }).notNull(),
    audienceCursor: integer().notNull().default(0),
    recipients: integer().notNull().default(0),
    /** Pod đang chia trang giữ tới lúc này; quá hạn thì pod khác tiếp tục từ audience_cursor. */
    leaseUntil: timestamp({ withTimezone: true }),
    createdBy: text().notNull(),
    idempotencyKey: text().notNull(),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    completedAt: timestamp({ withTimezone: true }),
    cancelledAt: timestamp({ withTimezone: true }),
  },
  (t) => [
    uniqueIndex("broadcasts_idempotency_key").on(t.createdBy, t.idempotencyKey),
    index("broadcasts_due_idx").on(t.status, t.scheduledAt),
    check("broadcasts_category_check", sql`category IN ('PROMOTION', 'SYSTEM')`),
    check("broadcasts_title_check", sql`length(btrim(title)) > 0`),
    check("broadcasts_body_check", sql`length(btrim(body)) > 0`),
    check("broadcasts_channels_check", sql`cardinality(channels) > 0`),
    check("broadcasts_cursor_check", sql`audience_cursor >= 0 AND recipients >= 0`),
  ],
);

export const deliveries = notificationSchema.table(
  "deliveries",
  {
    id: snowflakeId().primaryKey(),
    notificationId: snowflakeId()
      .notNull()
      .references(() => notifications.id, { onDelete: "cascade" }),
    channel: channel().notNull(),
    status: deliveryStatus().notNull().default("PENDING"),
    attempts: integer().notNull().default(0),
    lastError: text(),
    nextAttemptAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    sentAt: timestamp({ withTimezone: true }),
    /** Số nhỏ gửi trước: giao dịch 0, thường 5, hàng loạt 9 (ADR 0034). */
    priority: smallint().notNull().default(5),
    broadcastId: snowflakeId().references(() => broadcasts.id, { onDelete: "set null" }),
  },
  (t) => [
    uniqueIndex("deliveries_notification_channel_key").on(t.notificationId, t.channel),
    index("deliveries_due_idx")
      .on(t.channel, t.priority, t.nextAttemptAt)
      .where(sql`status = 'PENDING'`),
    index("deliveries_broadcast_idx")
      .on(t.broadcastId, t.status)
      .where(sql`broadcast_id IS NOT NULL`),
    check("deliveries_priority_check", sql`priority BETWEEN 0 AND 9`),
  ],
);

export const preferences = notificationSchema.table("preferences", {
  userId: text().primaryKey(),
  disabled: text().array().notNull(),
  quietHours: jsonb().$type<{ start: string; end: string; timezone: string }>(),
  updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
});

export const webPushSubscriptions = notificationSchema.table(
  "web_push_subscriptions",
  {
    endpoint: text().primaryKey(),
    userId: text().notNull(),
    p256dh: text().notNull(),
    auth: text().notNull(),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index("web_push_user_idx").on(t.userId)],
);

export const fcmTokens = notificationSchema.table(
  "fcm_tokens",
  {
    token: text().primaryKey(),
    userId: text().notNull(),
    platform: varchar({ length: 20 }).notNull(),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index("fcm_tokens_user_idx").on(t.userId)],
);

/** Bản sao cục bộ từ event: đơn → khách. */
export const orderCustomers = notificationSchema.table("order_customers", {
  orderId: text().primaryKey(),
  customerId: text().notNull(),
  createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
});

/** Ai đang theo dõi (wishlist) sản phẩm nào. */
export const productWatchers = notificationSchema.table(
  "product_watchers",
  {
    productId: text().notNull(),
    customerId: text().notNull(),
  },
  (t) => [primaryKey({ columns: [t.productId, t.customerId] })],
);

/** Giá/tồn mới nhất của sản phẩm được theo dõi, để phát hiện giảm giá/có hàng lại. */
export const productStates = notificationSchema.table("product_states", {
  productId: text().primaryKey(),
  name: text(),
  priceMinor: bigint({ mode: "number" }),
  currency: varchar({ length: 3 }),
  available: integer(),
  updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
});

export const { outbox, processedEvents } = platformTables(notificationSchema);
