import type { ChatMessage, Conversation, ConversationKind } from '../domain/conversation.js';

export interface ConversationSummary extends Conversation {
  unread: number;
}

export interface ConversationRepository {
  find(id: string): Promise<Conversation | undefined>;
  /** Tìm hội thoại sẵn có (một hội thoại cho mỗi cặp khách–seller, một hội thoại hỗ trợ mỗi khách). */
  findOpen(kind: ConversationKind, customerId: string, counterpartId: string): Promise<Conversation | undefined>;
  insert(c: Conversation): Promise<Conversation>;
  /** Cấp seq tiếp theo (khoá hàng hội thoại) và cập nhật tin cuối. */
  nextSeq(id: string, at: Date, preview: string): Promise<number>;
  listFor(
    userId: string,
    includeSupportInbox: boolean,
    limit: number,
    offset: number,
  ): Promise<{ items: ConversationSummary[]; total: number }>;
  markRead(conversationId: string, userId: string, seq: number): Promise<void>;
  unreadTotal(userId: string): Promise<number>;
}

export interface MessageRepository {
  insert(m: ChatMessage): Promise<void>;
  findByClientId(conversationId: string, senderId: string, clientMessageId: string): Promise<ChatMessage | undefined>;
  /** Tin cũ hơn `beforeSeq` (phân trang lùi), mới nhất trước. */
  page(conversationId: string, beforeSeq: number | null, limit: number): Promise<ChatMessage[]>;
}

export interface MessageEvents {
  sent(c: Conversation, m: ChatMessage): Promise<void>;
}

export interface TransactionScope {
  conversations: ConversationRepository;
  messages: MessageRepository;
  events: MessageEvents;
}

export interface UnitOfWork {
  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T>;
}

export type ReadRepositories = Omit<TransactionScope, 'events'>;

export type RealtimeEvent =
  | { kind: 'message'; conversationId: string; message: ChatMessage }
  | { kind: 'typing'; conversationId: string; userId: string };

export interface Realtime {
  /** Gửi tới từng người dùng; `toSupport` = kênh chung của nhân viên hỗ trợ. */
  publish(userIds: readonly string[], toSupport: boolean, event: RealtimeEvent): Promise<void>;
  /** Luồng sự kiện cho một người dùng (kèm kênh hỗ trợ chung nếu là nhân viên), cho GraphQL subscription. */
  stream(userId: string, includeSupport: boolean): AsyncIterableIterator<RealtimeEvent>;
}

export interface RateLimiter {
  /** true nếu còn trong hạn mức. */
  allow(key: string, limit: number, windowSeconds: number): Promise<boolean>;
}

export interface ProductDirectory {
  sellerOf(productId: string): Promise<string | undefined>;
}

export interface Clock {
  now(): Date;
}
export interface IdGenerator {
  next(): string;
}

export const UNIT_OF_WORK = Symbol('UNIT_OF_WORK');
export const READ_REPOSITORIES = Symbol('READ_REPOSITORIES');
export const REALTIME = Symbol('REALTIME');
export const RATE_LIMITER = Symbol('RATE_LIMITER');
export const PRODUCT_DIRECTORY = Symbol('PRODUCT_DIRECTORY');
export const CLOCK = Symbol('CLOCK');
export const ID_GENERATOR = Symbol('ID_GENERATOR');
export const MEDIA_PREFIX = Symbol('MEDIA_PREFIX');
