import { platformTables } from '@ecom/platform';
import { sql } from 'drizzle-orm';
import {
  boolean,
  doublePrecision,
  index,
  pgSchema,
  primaryKey,
  text,
  timestamp,
  uniqueIndex,
  varchar,
} from 'drizzle-orm/pg-core';

export const customerSchema = pgSchema('customer_service');

export const addressLabel = customerSchema.enum('address_label', ['HOME', 'OFFICE', 'OTHER']);

export const shippingAddresses = customerSchema.table(
  'shipping_addresses',
  {
    id: text().primaryKey(),
    customerId: text().notNull(),
    fullName: varchar({ length: 120 }).notNull(),
    phoneNumber: varchar({ length: 20 }).notNull(),
    label: addressLabel().notNull().default('HOME'),
    countryCode: varchar({ length: 2 }).notNull(),
    provinceCode: varchar({ length: 20 }),
    districtCode: varchar({ length: 20 }),
    wardCode: varchar({ length: 20 }),
    addressLine: text().notNull(),
    postalCode: varchar({ length: 20 }),
    latitude: doublePrecision(),
    longitude: doublePrecision(),
    isDefault: boolean().notNull().default(false),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    index('shipping_addresses_customer_idx').on(t.customerId),
    // Cơ sở dữ liệu tự bảo đảm mỗi khách chỉ có một địa chỉ mặc định.
    uniqueIndex('shipping_addresses_one_default').on(t.customerId).where(sql`${t.isDefault}`),
  ],
);

export const wishlistItems = customerSchema.table(
  'wishlist_items',
  {
    customerId: text().notNull(),
    productId: text().notNull(),
    addedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    primaryKey({ columns: [t.customerId, t.productId] }),
    index('wishlist_items_recent_idx').on(t.customerId, t.addedAt),
  ],
);

export const searchHistory = customerSchema.table(
  'search_history',
  {
    userId: text().notNull(),
    keyword: varchar({ length: 200 }).notNull(),
    searchedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    primaryKey({ columns: [t.userId, t.keyword] }),
    index('search_history_recent_idx').on(t.userId, t.searchedAt),
  ],
);

export const { outbox, processedEvents } = platformTables(customerSchema);
