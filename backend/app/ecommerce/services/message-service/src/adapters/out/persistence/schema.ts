import { platformTables } from '@ecommerce/platform';
import { bigint, index, pgSchema, primaryKey, text, timestamp, uniqueIndex, varchar } from 'drizzle-orm/pg-core';

export const messageSchema = pgSchema('message_service');

export const conversationKind = messageSchema.enum('conversation_kind', ['SELLER', 'SUPPORT']);

export const conversations = messageSchema.table(
  'conversations',
  {
    id: text().primaryKey(),
    kind: conversationKind().notNull(),
    customerId: text().notNull(),
    counterpartId: text().notNull(),
    productId: text(),
    orderId: text(),
    lastSeq: bigint({ mode: 'number' }).notNull().default(0),
    lastMessageAt: timestamp({ withTimezone: true }),
    lastMessagePreview: varchar({ length: 120 }),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex('conversations_pair_key').on(t.kind, t.customerId, t.counterpartId),
    index('conversations_customer_idx').on(t.customerId, t.lastMessageAt),
    index('conversations_counterpart_idx').on(t.counterpartId, t.lastMessageAt),
  ],
);

export const messages = messageSchema.table(
  'messages',
  {
    id: text().primaryKey(),
    conversationId: text()
      .notNull()
      .references(() => conversations.id, { onDelete: 'cascade' }),
    seq: bigint({ mode: 'number' }).notNull(),
    senderId: text().notNull(),
    body: text().notNull(),
    attachments: text().array().notNull(),
    clientMessageId: varchar({ length: 64 }),
    createdAt: timestamp({ withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex('messages_seq_key').on(t.conversationId, t.seq),
    uniqueIndex('messages_client_id_key').on(t.conversationId, t.senderId, t.clientMessageId),
  ],
);

export const conversationReads = messageSchema.table(
  'conversation_reads',
  {
    conversationId: text()
      .notNull()
      .references(() => conversations.id, { onDelete: 'cascade' }),
    userId: text().notNull(),
    lastReadSeq: bigint({ mode: 'number' }).notNull().default(0),
  },
  (t) => [primaryKey({ columns: [t.conversationId, t.userId] })],
);

export const { outbox, processedEvents } = platformTables(messageSchema);
