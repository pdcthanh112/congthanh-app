import { customType, type PgSchema, primaryKey, text, timestamp, uuid } from 'drizzle-orm/pg-core';

export const bytea = customType<{ data: Uint8Array; driverData: Buffer }>({
  dataType: () => 'bytea',
  toDriver: (v) => Buffer.from(v.buffer, v.byteOffset, v.byteLength),
  fromDriver: (v) => new Uint8Array(v),
});

/**
 * Bảng nền trong schema của từng service, cùng cấu trúc với service Java/Go:
 * - outbox: Debezium Outbox Event Router đọc WAL và đẩy lên topic ecommerce.<aggregatetype>.events
 * - processed_events: consumer Kafka ghi event_id cùng transaction với tác dụng phụ để chống xử lý trùng
 *
 * Service khai báo trong schema Drizzle của mình: `export const { outbox, processedEvents } = platformTables(svc)`
 * để drizzle-kit sinh migration cho cả hai bảng.
 */
export function platformTables(schema: PgSchema) {
  const outbox = schema.table('outbox', {
    id: uuid('id').primaryKey(),
    aggregatetype: text('aggregatetype').notNull(),
    aggregateid: text('aggregateid').notNull(),
    type: text('type').notNull(),
    payload: bytea('payload').notNull(),
    traceparent: text('traceparent'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  });
  const processedEvents = schema.table(
    'processed_events',
    {
      eventId: text('event_id').notNull(),
      consumer: text('consumer').notNull(),
      processedAt: timestamp('processed_at', { withTimezone: true }).notNull().defaultNow(),
    },
    (t) => [primaryKey({ columns: [t.eventId, t.consumer] })],
  );
  return { outbox, processedEvents };
}

export type PlatformTables = ReturnType<typeof platformTables>;
