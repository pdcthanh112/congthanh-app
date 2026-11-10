import { platformTables } from '@ecom/platform';
import { sql } from 'drizzle-orm';
import {
  bigint,
  check,
  index,
  integer,
  pgSchema,
  primaryKey,
  smallint,
  text,
  timestamp,
  uniqueIndex,
  varchar,
} from 'drizzle-orm/pg-core';

export const reviewSchema = pgSchema('review_service');

export const reviewStatus = reviewSchema.enum('review_status', ['PUBLISHED', 'HIDDEN']);
export const purchaseStatus = reviewSchema.enum('purchase_status', ['PENDING', 'CONFIRMED', 'CANCELLED']);

export const reviews = reviewSchema.table(
  'reviews',
  {
    id: text().primaryKey(),
    productId: text().notNull(),
    customerId: text().notNull(),
    authorName: varchar({ length: 120 }).notNull(),
    orderId: text().notNull(),
    rating: smallint().notNull(),
    title: varchar({ length: 150 }),
    content: text(),
    mediaUrls: text().array().notNull().default(sql`'{}'::text[]`),
    status: reviewStatus().notNull().default('PUBLISHED'),
    moderationReason: text(),
    helpfulCount: integer().notNull().default(0),
    sellerReply: text(),
    repliedAt: timestamp({ withTimezone: true }),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    version: integer().notNull().default(0),
  },
  (t) => [
    uniqueIndex('reviews_one_per_purchase').on(t.customerId, t.orderId, t.productId),
    index('reviews_product_recent_idx').on(t.productId, t.status, t.createdAt),
    index('reviews_product_helpful_idx').on(t.productId, t.status, t.helpfulCount),
    index('reviews_customer_idx').on(t.customerId),
    check('reviews_rating_range', sql`${t.rating} between 1 and 5`),
  ],
);

export const helpfulVotes = reviewSchema.table(
  'helpful_votes',
  {
    reviewId: text()
      .notNull()
      .references(() => reviews.id, { onDelete: 'cascade' }),
    userId: text().notNull(),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [primaryKey({ columns: [t.reviewId, t.userId] })],
);

export const purchases = reviewSchema.table(
  'purchases',
  {
    orderId: text().notNull(),
    productId: text().notNull(),
    customerId: text().notNull(),
    status: purchaseStatus().notNull().default('PENDING'),
    confirmedAt: timestamp({ withTimezone: true }),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    primaryKey({ columns: [t.orderId, t.productId] }),
    index('purchases_customer_idx').on(t.customerId, t.status),
  ],
);

export const ratingSummaries = reviewSchema.table('rating_summaries', {
  productId: text().primaryKey(),
  star1: bigint({ mode: 'number' }).notNull().default(0),
  star2: bigint({ mode: 'number' }).notNull().default(0),
  star3: bigint({ mode: 'number' }).notNull().default(0),
  star4: bigint({ mode: 'number' }).notNull().default(0),
  star5: bigint({ mode: 'number' }).notNull().default(0),
  updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
});

export const { outbox, processedEvents } = platformTables(reviewSchema);
