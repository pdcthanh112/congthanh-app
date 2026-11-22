import { platformTables, snowflakeId } from "@ecommerce/platform";
import { sql } from "drizzle-orm";
import { type AnyPgColumn, bigint, boolean, char, check, index, integer, pgSchema, primaryKey, smallint, text, timestamp, uniqueIndex, varchar } from "drizzle-orm/pg-core";

export const catalogSchema = pgSchema("catalog_service");

export const categoryStatus = catalogSchema.enum("category_status", ["ACTIVE", "INACTIVE"]);
export const tagStatus = catalogSchema.enum("tag_status", ["ACTIVE", "INACTIVE"]);

export const categories = catalogSchema.table(
  "categories",
  {
    id: snowflakeId().primaryKey(),
    name: varchar({ length: 120 }).notNull(),
    slug: varchar({ length: 120 }).notNull(),
    description: text(),
    image: text(),
    parentId: snowflakeId().references((): AnyPgColumn => categories.id, { onDelete: "restrict" }),
    status: categoryStatus().notNull().default("ACTIVE"),
    sortOrder: integer().notNull().default(0),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    version: integer().notNull().default(0),
  },
  (t) => [uniqueIndex("categories_slug_key").on(t.slug), index("categories_parent_idx").on(t.parentId, t.sortOrder)],
);

export const brands = catalogSchema.table(
  "brands",
  {
    id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity(),
    name: varchar({ length: 120 }).notNull(),
    slug: varchar({ length: 120 }).notNull(),
    image: text(),
    active: boolean().notNull().default(true),
  },
  (t) => [uniqueIndex("brands_slug_key").on(t.slug)],
);

export const tags = catalogSchema.table(
  "tags",
  {
    id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity(),
    name: varchar({ length: 50 }).notNull(),
    status: tagStatus().notNull().default("ACTIVE"),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex("tags_name_key").on(t.name)],
);

export const productTags = catalogSchema.table(
  "product_tags",
  {
    productId: text().notNull(),
    tagId: bigint({ mode: "number" })
      .notNull()
      .references(() => tags.id, { onDelete: "cascade" }),
  },
  (t) => [primaryKey({ columns: [t.productId, t.tagId] }), index("product_tags_tag_idx").on(t.tagId)],
);

export const countries = catalogSchema.table(
  "countries",
  {
    id: bigint({ mode: "number" }).primaryKey().generatedByDefaultAsIdentity(),
    isoCode: char({ length: 2 }).notNull(),
    isoCode3: char({ length: 3 }),
    countryCode: varchar({ length: 3 }),
    name: varchar({ length: 120 }).notNull(),
    nameOrigin: varchar({ length: 120 }),
    phoneCode: varchar({ length: 10 }),
    currencyCode: varchar({ length: 3 }),
    currencySymbol: varchar({ length: 10 }),
    language: varchar({ length: 20 }),
    locale: varchar({ length: 10 }),
    timezone: varchar({ length: 40 }),
  },
  (t) => [uniqueIndex("countries_iso_key").on(sql`upper(${t.isoCode})`)],
);

export const currencies = catalogSchema.table(
  "currencies",
  {
    code: char({ length: 3 }).primaryKey(),
    numericCode: char({ length: 3 }).notNull(),
    name: varchar({ length: 80 }).notNull(),
    nativeName: varchar({ length: 80 }),
    symbol: varchar({ length: 10 }).notNull(),
    nativeSymbol: varchar({ length: 10 }),
    decimalDigits: smallint().notNull(),
    countries: char({ length: 2 })
      .array()
      .notNull()
      .default(sql`'{}'::char(2)[]`),
    active: boolean().notNull().default(true),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex("currencies_numeric_code_key").on(t.numericCode),
    index("currencies_countries_idx").using("gin", t.countries),
    check("currencies_code_check", sql`code ~ '^[A-Z]{3}$'`),
    check("currencies_numeric_code_check", sql`numeric_code ~ '^[0-9]{3}$'`),
    check("currencies_decimal_digits_check", sql`decimal_digits BETWEEN 0 AND 4`),
  ],
);

export const { outbox, processedEvents } = platformTables(catalogSchema);
