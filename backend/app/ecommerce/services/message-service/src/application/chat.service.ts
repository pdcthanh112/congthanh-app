import { DomainError } from '@ecom/platform/errors';
import { Inject, Injectable } from '@nestjs/common';
import {
  assertAccess,
  type ChatMessage,
  type Conversation,
  normalizeBody,
  previewOf,
  recipientsOf,
  SUPPORT_INBOX,
  type Viewer,
} from '../domain/conversation.js';
import {
  CLOCK,
  type Clock,
  ID_GENERATOR,
  type IdGenerator,
  MEDIA_PREFIX,
  PRODUCT_DIRECTORY,
  type ProductDirectory,
  RATE_LIMITER,
  type RateLimiter,
  READ_REPOSITORIES,
  REALTIME,
  type ReadRepositories,
  type Realtime,
  type TransactionScope,
  UNIT_OF_WORK,
  type UnitOfWork,
} from './ports.js';

export interface SendInput {
  body: string;
  attachments: readonly string[];
  clientMessageId?: string | null | undefined;
}

const MESSAGES_PER_MINUTE = 30;

@Injectable()
export class ChatService {
  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(READ_REPOSITORIES) readonly read: ReadRepositories,
    @Inject(REALTIME) private readonly realtime: Realtime,
    @Inject(RATE_LIMITER) private readonly limiter: RateLimiter,
    @Inject(PRODUCT_DIRECTORY) private readonly products: ProductDirectory,
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(ID_GENERATOR) private readonly ids: IdGenerator,
    @Inject(MEDIA_PREFIX) private readonly mediaPrefix: string,
  ) {}

  /** Khách hỏi người bán về một sản phẩm: người bán xác định từ product-service, không tin client. */
  async startWithSeller(customer: Viewer, productId: string, first: SendInput) {
    const sellerId = await this.products.sellerOf(productId);
    if (!sellerId) throw DomainError.notFound(`product ${productId} not found`);
    if (sellerId === customer.id) throw DomainError.invalid('cannot chat with yourself');
    return this.start(customer, 'SELLER', sellerId, { productId, orderId: null }, first);
  }

  startSupport(customer: Viewer, orderId: string | null, first: SendInput) {
    return this.start(customer, 'SUPPORT', SUPPORT_INBOX, { productId: null, orderId }, first);
  }

  async send(viewer: Viewer, conversationId: string, input: SendInput): Promise<ChatMessage> {
    const { conversation, message, created } = await this.uow.run(async (tx) => {
      const c = assertAccess(await tx.conversations.find(conversationId), viewer);
      return { conversation: c, ...(await this.appendIn(tx, c, viewer, input)) };
    });
    if (created) await this.fanOut(conversation, message);
    return message;
  }

  async typing(viewer: Viewer, conversationId: string): Promise<void> {
    const c = assertAccess(await this.read.conversations.find(conversationId), viewer);
    if (!(await this.limiter.allow(`typing:${viewer.id}`, 30, 60))) return;
    await this.realtime.publish(recipientsOf(c, viewer.id), c.kind === 'SUPPORT' && viewer.id === c.customerId, {
      kind: 'typing',
      conversationId,
      userId: viewer.id,
    });
  }

  async history(viewer: Viewer, conversationId: string, beforeSeq: number | null, limit: number) {
    assertAccess(await this.read.conversations.find(conversationId), viewer);
    return this.read.messages.page(conversationId, beforeSeq, Math.min(Math.max(limit, 1), 100));
  }

  async conversation(viewer: Viewer, id: string) {
    return assertAccess(await this.read.conversations.find(id), viewer);
  }

  list(viewer: Viewer, page: number, size: number) {
    const limit = Math.min(Math.max(size, 1), 50);
    return this.read.conversations.listFor(viewer.id, viewer.isSupport, limit, (Math.max(page, 1) - 1) * limit);
  }

  async markRead(viewer: Viewer, conversationId: string, seq: number) {
    const c = assertAccess(await this.read.conversations.find(conversationId), viewer);
    await this.read.conversations.markRead(c.id, viewer.id, Math.min(seq, c.lastSeq));
  }

  // ---------- nội bộ ----------

  private async start(
    customer: Viewer,
    kind: Conversation['kind'],
    counterpartId: string,
    subject: Pick<Conversation, 'productId' | 'orderId'>,
    first: SendInput,
  ) {
    const { conversation, message, created } = await this.uow.run(async (tx) => {
      const existing = await tx.conversations.findOpen(kind, customer.id, counterpartId);
      const c =
        existing ??
        (await tx.conversations.insert({
          id: this.ids.next(),
          kind,
          customerId: customer.id,
          counterpartId,
          ...subject,
          lastSeq: 0,
          lastMessageAt: null,
          lastMessagePreview: null,
          createdAt: this.clock.now(),
        }));
      return { conversation: c, ...(await this.appendIn(tx, c, customer, first)) };
    });
    if (created) await this.fanOut(conversation, message);
    return { conversation, message };
  }

  /** Ghi tin trong transaction: idempotent theo clientMessageId (client gửi lại khi mất mạng không bị trùng). */
  private async appendIn(tx: TransactionScope, c: Conversation, sender: Viewer, input: SendInput) {
    if (input.clientMessageId) {
      const dup = await tx.messages.findByClientId(c.id, sender.id, input.clientMessageId);
      if (dup) return { message: dup, created: false };
    }
    if (!(await this.limiter.allow(`send:${sender.id}`, MESSAGES_PER_MINUTE, 60))) {
      throw DomainError.invalid('you are sending messages too fast', 'RATE_LIMITED');
    }
    const content = normalizeBody(input.body, input.attachments, this.mediaPrefix);
    const now = this.clock.now();
    const seq = await tx.conversations.nextSeq(c.id, now, previewOf(content));
    const message: ChatMessage = {
      id: this.ids.next(),
      conversationId: c.id,
      seq,
      senderId: sender.id,
      ...content,
      clientMessageId: input.clientMessageId ?? null,
      createdAt: now,
    };
    await tx.messages.insert(message);
    await tx.conversations.markRead(c.id, sender.id, seq);
    await tx.events.sent(c, message);
    return { message, created: true };
  }

  private async fanOut(c: Conversation, m: ChatMessage) {
    const toSupport = c.kind === 'SUPPORT';
    // Cả người gửi cũng nhận (đồng bộ nhiều thiết bị/tab của chính họ).
    await this.realtime
      .publish([...new Set([c.customerId, ...recipientsOf(c, ''), m.senderId])], toSupport, {
        kind: 'message',
        conversationId: c.id,
        message: m,
      })
      .catch(() => undefined);
  }
}
