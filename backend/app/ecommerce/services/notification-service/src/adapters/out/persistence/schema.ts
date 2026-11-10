import { platformTables } from '@ecom/platform';
import {
  bigint,
  index,
  integer,
  jsonb,
  pgSchema,
  primaryKey,
  text,
  timestamp,
  uniqueIndex,
  varchar,
} from 'drizzle-orm/pg-core';

export const notificationSchema = pgSchema('notification_service');

export const channel = notificationSchema.enum('channel', ['IN_APP', 'EMAIL', 'SMS', 'WEB_PUSH', 'FCM']);
export const deliveryStatus = notificationSchema.enum('delivery_status', ['PENDING', 'SENT', 'FAILED', 'SKIPPED']);

export const notifications = notificationSchema.table(
  'notifications',
  {
    id: text().primaryKey(),
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
  (t) => [
    uniqueIndex('notifications_dedup_key').on(t.dedupKey),
    index('notifications_user_recent_idx').on(t.userId, t.createdAt),
  ],
);

export const deliveries = notificationSchema.table(
  'deliveries',
  {
    id: text().primaryKey(),
    notificationId: text()
      .notNull()
      .references(() => notifications.id, { onDelete: 'cascade' }),
    channel: channel().notNull(),
    status: deliveryStatus().notNull().default('PENDING'),
    attempts: integer().notNull().default(0),
    lastError: text(),
    nextAttemptAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    sentAt: timestamp({ withTimezone: true }),
  },
  (t) => [index('deliveries_due_idx').on(t.status, t.nextAttemptAt)],
);

export const preferences = notificationSchema.table('preferences', {
  userId: text().primaryKey(),
  disabled: text().array().notNull(),
  quietHours: jsonb().$type<{ start: string; end: string; timezone: string }>(),
  updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
});

export const webPushSubscriptions = notificationSchema.table(
  'web_push_subscriptions',
  {
    endpoint: text().primaryKey(),
    userId: text().notNull(),
    p256dh: text().notNull(),
    auth: text().notNull(),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index('web_push_user_idx').on(t.userId)],
);

export const fcmTokens = notificationSchema.table(
  'fcm_tokens',
  {
    token: text().primaryKey(),
    userId: text().notNull(),
    platform: varchar({ length: 20 }).notNull(),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index('fcm_tokens_user_idx').on(t.userId)],
);

/** Bản sao cục bộ từ event: đơn → khách. */
export const orderCustomers = notificationSchema.table('order_customers', {
  orderId: text().primaryKey(),
  customerId: text().notNull(),
  createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
});

/** Ai đang theo dõi (wishlist) sản phẩm nào. */
export const productWatchers = notificationSchema.table(
  'product_watchers',
  {
    productId: text().notNull(),
    customerId: text().notNull(),
  },
  (t) => [primaryKey({ columns: [t.productId, t.customerId] })],
);

/** Giá/tồn mới nhất của sản phẩm được theo dõi, để phát hiện giảm giá/có hàng lại. */
export const productStates = notificationSchema.table('product_states', {
  productId: text().primaryKey(),
  name: text(),
  priceMinor: bigint({ mode: 'number' }),
  currency: varchar({ length: 3 }),
  available: integer(),
  updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
});

export const { outbox, processedEvents } = platformTables(notificationSchema);
