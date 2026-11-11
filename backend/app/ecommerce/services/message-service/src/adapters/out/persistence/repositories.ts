import type { Database } from '@ecommerce/platform';
import { and, desc, eq, lt, or, sql } from 'drizzle-orm';
import type { ConversationRepository, ConversationSummary, MessageRepository } from '../../../application/ports.js';
import type { ChatMessage, Conversation } from '../../../domain/conversation.js';
import type * as schema from './schema.js';
import { conversationReads, conversations, messages } from './schema.js';

export type Conn = Pick<Database<typeof schema>, 'select' | 'insert' | 'update' | 'delete' | 'execute'>;

export class DrizzleConversationRepository implements ConversationRepository {
  constructor(private readonly db: Conn) {}

  async find(id: string) {
    const [row] = await this.db.select().from(conversations).where(eq(conversations.id, id));
    return row;
  }

  async findOpen(kind: Conversation['kind'], customerId: string, counterpartId: string) {
    const [row] = await this.db
      .select()
      .from(conversations)
      .where(
        and(
          eq(conversations.kind, kind),
          eq(conversations.customerId, customerId),
          eq(conversations.counterpartId, counterpartId),
        ),
      );
    return row;
  }

  async insert(c: Conversation) {
    // Hai request mở hội thoại cùng lúc: bên thua dùng lại hội thoại của bên thắng.
    const [row] = await this.db.insert(conversations).values(c).onConflictDoNothing().returning();
    return row ?? (await this.findOpen(c.kind, c.customerId, c.counterpartId))!;
  }

  async nextSeq(id: string, at: Date, preview: string) {
    const [row] = await this.db
      .update(conversations)
      .set({ lastSeq: sql`${conversations.lastSeq} + 1`, lastMessageAt: at, lastMessagePreview: preview })
      .where(eq(conversations.id, id))
      .returning({ seq: conversations.lastSeq });
    return row!.seq;
  }

  async listFor(userId: string, includeSupportInbox: boolean, limit: number, offset: number) {
    const mine = or(
      eq(conversations.customerId, userId),
      eq(conversations.counterpartId, userId),
      includeSupportInbox ? eq(conversations.kind, 'SUPPORT') : sql`false`,
    );
    const unread = sql<number>`greatest(${conversations.lastSeq} - coalesce(${conversationReads.lastReadSeq}, 0), 0)::int`;
    const [rows, [total]] = await Promise.all([
      this.db
        .select({ c: conversations, unread })
        .from(conversations)
        .leftJoin(
          conversationReads,
          and(eq(conversationReads.conversationId, conversations.id), eq(conversationReads.userId, userId)),
        )
        .where(mine)
        .orderBy(sql`${conversations.lastMessageAt} desc nulls last`)
        .limit(limit)
        .offset(offset),
      this.db.select({ n: sql<number>`count(*)::int` }).from(conversations).where(mine),
    ]);
    return { items: rows.map((r): ConversationSummary => ({ ...r.c, unread: r.unread })), total: total?.n ?? 0 };
  }

  async markRead(conversationId: string, userId: string, seq: number) {
    await this.db
      .insert(conversationReads)
      .values({ conversationId, userId, lastReadSeq: seq })
      .onConflictDoUpdate({
        target: [conversationReads.conversationId, conversationReads.userId],
        // Không lùi mốc đã đọc khi client gửi seq cũ.
        set: { lastReadSeq: sql`greatest(${conversationReads.lastReadSeq}, excluded.last_read_seq)` },
      });
  }

  async unreadTotal(userId: string) {
    const [row] = await this.db
      .select({
        n: sql<number>`coalesce(sum(greatest(${conversations.lastSeq} - coalesce(${conversationReads.lastReadSeq}, 0), 0)), 0)::int`,
      })
      .from(conversations)
      .leftJoin(
        conversationReads,
        and(eq(conversationReads.conversationId, conversations.id), eq(conversationReads.userId, userId)),
      )
      .where(or(eq(conversations.customerId, userId), eq(conversations.counterpartId, userId)));
    return row?.n ?? 0;
  }
}

export class DrizzleMessageRepository implements MessageRepository {
  constructor(private readonly db: Conn) {}

  async insert(m: ChatMessage) {
    await this.db.insert(messages).values(m);
  }

  async findByClientId(conversationId: string, senderId: string, clientMessageId: string) {
    const [row] = await this.db
      .select()
      .from(messages)
      .where(
        and(
          eq(messages.conversationId, conversationId),
          eq(messages.senderId, senderId),
          eq(messages.clientMessageId, clientMessageId),
        ),
      );
    return row;
  }

  page(conversationId: string, beforeSeq: number | null, limit: number) {
    const cond = beforeSeq
      ? and(eq(messages.conversationId, conversationId), lt(messages.seq, beforeSeq))
      : eq(messages.conversationId, conversationId);
    return this.db.select().from(messages).where(cond).orderBy(desc(messages.seq)).limit(limit);
  }
}
