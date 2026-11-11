import { create, type DescMessage, type MessageShape, toBinary } from '@bufbuild/protobuf';
import { anyPack, timestampFromDate } from '@bufbuild/protobuf/wkt';
import { type EventEnvelope, EventEnvelopeSchema } from '@ecommerce/proto/ecommerce/common/v1/event_envelope_pb';
import { trace } from '@opentelemetry/api';
import { v7 as uuidv7 } from 'uuid';
import type { Database } from './database.js';
import type { PlatformTables } from './platform-tables.js';

export interface OutboxEvent<D extends DescMessage> {
  aggregateType: string;
  aggregateId: string;
  schema: D;
  payload: MessageShape<D>;
}

/** `db` hoặc `tx` của Drizzle (transaction cũng có insert). */
export type Writer = Pick<Database, 'insert'>;

/**
 * Ghi event vào outbox trong transaction đang chạy (truyền `tx` của Drizzle). Payload là
 * ecommerce.common.v1.EventEnvelope bọc event trong Any, giống hệt service Java/Go.
 */
export class OutboxWriter {
  constructor(
    private readonly source: string,
    private readonly table: PlatformTables['outbox'],
    private readonly now: () => Date = () => new Date(),
  ) {}

  async write<D extends DescMessage>(tx: Writer, event: OutboxEvent<D>): Promise<string> {
    const env = this.envelope(event);
    await tx.insert(this.table).values({
      id: env.id,
      aggregatetype: env.aggregateType,
      aggregateid: env.aggregateId,
      type: env.type,
      payload: toBinary(EventEnvelopeSchema, env),
      traceparent: env.traceparent || null,
    });
    return env.id;
  }

  envelope<D extends DescMessage>(event: OutboxEvent<D>): EventEnvelope {
    return create(EventEnvelopeSchema, {
      id: uuidv7(),
      type: event.schema.typeName,
      source: this.source,
      aggregateType: event.aggregateType,
      aggregateId: event.aggregateId,
      occurredAt: timestampFromDate(this.now()),
      traceparent: currentTraceparent() ?? '',
      payload: anyPack(event.schema, event.payload),
    });
  }
}

/** W3C traceparent của span hiện tại để trace nối qua Kafka. */
export function currentTraceparent(): string | undefined {
  const ctx = trace.getActiveSpan()?.spanContext();
  if (!ctx || ctx.traceId === '00000000000000000000000000000000') return undefined;
  return `00-${ctx.traceId}-${ctx.spanId}-0${ctx.traceFlags.toString(16)}`;
}
