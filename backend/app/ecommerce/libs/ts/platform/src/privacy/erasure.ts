import { createHash } from 'node:crypto';
import { create } from '@bufbuild/protobuf';
import {
  ErasureCompletedSchema,
  ErasureRequestedSchema,
} from '@ecommerce/proto/ecommerce/privacy/v1/privacy_events_pb';
import type { Database, Tx } from '../db/database.js';
import { markProcessed } from '../db/inbox.js';
import { OutboxWriter } from '../db/outbox.js';
import type { PlatformTables } from '../db/platform-tables.js';
import { KafkaEventConsumer } from '../kafka/event-consumer.js';

export interface ErasureTarget {
  /** Tên processor, phải khớp registry của privacy-service (PRIVACY_PROCESSORS). */
  processor: string;
  /**
   * Xoá/ẩn danh dữ liệu của khách trong transaction. Trả lý do pháp lý nếu phải giữ lại một phần
   * (chứng từ thuế, chống gian lận...), null nếu đã xoá hết.
   */
  erase(tx: Tx, subjectId: string, emailSha256: string): Promise<string | null>;
}

/** Định danh thay thế ổn định, không đảo ngược được, cho dữ liệu phải giữ (ví dụ đánh giá đã ẩn danh). */
export function erasedId(subjectId: string): string {
  return `erased-${createHash('sha256').update(subjectId).digest('hex').slice(0, 16)}`;
}

/**
 * Nghe ErasureRequested trên ecommerce.privacy.events (ADR 0024/0027), xoá dữ liệu của khách và trả lời
 * ErasureCompleted qua outbox (aggregatetype "privacy") trong CÙNG transaction: không bao giờ báo xong khi chưa xoá.
 */
export function privacyErasureConsumer(opts: {
  db: Database;
  tables: PlatformTables;
  source: string;
  target: ErasureTarget;
  brokers: string[];
  clientId: string;
}) {
  const writer = new OutboxWriter(opts.source, opts.tables.outbox);
  const consumer = `${opts.target.processor}-privacy`;
  return new KafkaEventConsumer({
    brokers: opts.brokers,
    clientId: `${opts.clientId}-privacy`,
    groupId: consumer,
    topics: ['ecommerce.privacy.events'],
  }).on(ErasureRequestedSchema, (e, env) =>
    opts.db.transaction(async (tx) => {
      if (!(await markProcessed(tx, opts.tables.processedEvents, env.id, consumer))) return;
      const retained = await opts.target.erase(tx, e.subjectId, e.emailSha256);
      await writer.write(tx, {
        aggregateType: 'privacy',
        aggregateId: e.subjectId,
        schema: ErasureCompletedSchema,
        payload: create(ErasureCompletedSchema, {
          requestId: e.requestId,
          subjectId: e.subjectId,
          processor: opts.target.processor,
          partiallyRetained: retained !== null,
          retentionReason: retained ?? '',
        }),
      });
    }),
  );
}
