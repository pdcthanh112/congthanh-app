import { create } from '@bufbuild/protobuf';
import { timestampFromDate } from '@bufbuild/protobuf/wkt';
import { type Database, DB, OutboxWriter } from '@ecom/platform';
import { MessageSentSchema } from '@ecom/proto/ecommerce/message/v1/message_events_pb';
import { Inject, Injectable } from '@nestjs/common';
import type { MessageEvents, ReadRepositories, TransactionScope, UnitOfWork } from '../../../application/ports.js';
import { type ChatMessage, type Conversation, previewOf, recipientsOf } from '../../../domain/conversation.js';
import { type Conn, DrizzleConversationRepository, DrizzleMessageRepository } from './repositories.js';
import type * as schema from './schema.js';
import { outbox } from './schema.js';

class OutboxMessageEvents implements MessageEvents {
  constructor(
    private readonly tx: Conn,
    private readonly writer: OutboxWriter,
  ) {}

  async sent(c: Conversation, m: ChatMessage) {
    await this.writer.write(this.tx, {
      aggregateType: 'message',
      aggregateId: c.id,
      schema: MessageSentSchema,
      payload: create(MessageSentSchema, {
        conversationId: c.id,
        messageId: m.id,
        seq: BigInt(m.seq),
        senderId: m.senderId,
        recipientIds: recipientsOf(c, m.senderId),
        preview: previewOf(m),
        sentAt: timestampFromDate(m.createdAt),
      }),
    });
  }
}

@Injectable()
export class DrizzleUnitOfWork implements UnitOfWork {
  private readonly writer = new OutboxWriter('message-service', outbox);

  constructor(@Inject(DB) private readonly db: Database<typeof schema>) {}

  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T> {
    return this.db.transaction((tx) =>
      work({
        conversations: new DrizzleConversationRepository(tx),
        messages: new DrizzleMessageRepository(tx),
        events: new OutboxMessageEvents(tx, this.writer),
      }),
    );
  }
}

export function readRepositories(db: Database<typeof schema>): ReadRepositories {
  return { conversations: new DrizzleConversationRepository(db), messages: new DrizzleMessageRepository(db) };
}
