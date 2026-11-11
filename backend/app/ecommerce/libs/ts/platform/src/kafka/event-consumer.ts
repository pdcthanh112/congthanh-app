import { type DescMessage, fromBinary, type MessageShape } from '@bufbuild/protobuf';
import { anyUnpack } from '@bufbuild/protobuf/wkt';
import { KafkaJS } from '@confluentinc/kafka-javascript';
import { type EventEnvelope, EventEnvelopeSchema } from '@ecommerce/proto/ecommerce/common/v1/event_envelope_pb';
import { Logger, type OnApplicationBootstrap, type OnApplicationShutdown } from '@nestjs/common';
import { context, propagation, SpanKind, SpanStatusCode, trace } from '@opentelemetry/api';

export interface ConsumerOptions {
  brokers: string[];
  clientId: string;
  groupId: string;
  topics: string[];
  /** Số lần thử lại trong tiến trình trước khi đẩy sang <topic>.dlq. */
  maxAttempts?: number;
}

export type EventHandler<D extends DescMessage> = (event: MessageShape<D>, envelope: EventEnvelope) => Promise<void>;

interface Registration {
  schema: DescMessage;
  handle: (event: unknown, envelope: EventEnvelope) => Promise<void>;
}

/**
 * Consumer Kafka cho event dạng EventEnvelope (protobuf) do Debezium đẩy từ outbox của mọi service.
 * - Đăng ký handler theo kiểu event: `consumer.on(OrderPlacedSchema, async (e, env) => ...)`; kiểu lạ bị bỏ qua.
 * - Commit offset thủ công sau khi handler xong; lỗi thì thử lại có backoff, quá số lần thì đẩy sang DLQ.
 * - Handler phải idempotent (dùng markProcessed với env.id) vì Kafka giao ít nhất một lần.
 * - Trace nối tiếp từ producer qua header traceparent.
 * Không có KAFKA_BROKERS thì không chạy (local không cần broker).
 */
export class KafkaEventConsumer implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger(KafkaEventConsumer.name);
  private readonly handlers = new Map<string, Registration>();
  private consumer?: KafkaJS.Consumer;
  private producer?: KafkaJS.Producer;
  private readonly tracer = trace.getTracer('@ecommerce/platform/kafka');

  constructor(private readonly opts: ConsumerOptions) {}

  on<D extends DescMessage>(schema: D, handle: EventHandler<D>): this {
    this.handlers.set(schema.typeName, {
      schema,
      handle: handle as Registration['handle'],
    });
    return this;
  }

  get enabled(): boolean {
    return this.opts.brokers.length > 0;
  }

  async onApplicationBootstrap(): Promise<void> {
    if (!this.enabled) {
      this.log.warn(`KAFKA_BROKERS not set; consumer ${this.opts.groupId} disabled`);
      return;
    }
    const kafka = new KafkaJS.Kafka({ kafkaJS: { clientId: this.opts.clientId, brokers: this.opts.brokers } });
    this.producer = kafka.producer({ kafkaJS: { idempotent: true } });
    this.consumer = kafka.consumer({
      kafkaJS: { groupId: this.opts.groupId, fromBeginning: true, autoCommit: false },
    });
    await this.producer.connect();
    await this.consumer.connect();
    await this.consumer.subscribe({ topics: this.opts.topics });
    await this.consumer.run({
      eachMessage: async ({ topic, partition, message }) => {
        await this.process(topic, message);
        await this.consumer?.commitOffsets([{ topic, partition, offset: (BigInt(message.offset) + 1n).toString() }]);
      },
    });
    this.log.log(`consuming ${this.opts.topics.join(', ')} as ${this.opts.groupId}`);
  }

  async onApplicationShutdown(): Promise<void> {
    await this.consumer?.disconnect();
    await this.producer?.disconnect();
  }

  /** Xử lý một record (public để test không cần broker). */
  async process(topic: string, message: KafkaJS.KafkaMessage): Promise<void> {
    if (!message.value) return;
    let envelope: EventEnvelope;
    try {
      envelope = fromBinary(EventEnvelopeSchema, message.value);
    } catch (e) {
      this.log.error(`undecodable record on ${topic}@${message.offset}: ${(e as Error).message}`);
      await this.deadLetter(topic, message, e as Error);
      return;
    }
    const reg = this.handlers.get(envelope.type);
    if (!reg) return;
    const event = envelope.payload ? anyUnpack(envelope.payload, reg.schema) : undefined;
    if (!event) {
      await this.deadLetter(topic, message, new Error(`payload is not ${envelope.type}`));
      return;
    }

    const parent = propagation.extract(context.active(), {
      traceparent: envelope.traceparent || headerOf(message, 'traceparent'),
    });
    await context.with(parent, () =>
      this.tracer.startActiveSpan(
        `${topic} process`,
        {
          kind: SpanKind.CONSUMER,
          attributes: {
            'messaging.system': 'kafka',
            'messaging.destination.name': topic,
            'messaging.consumer.group.name': this.opts.groupId,
            'messaging.message.id': envelope.id,
            'event.type': envelope.type,
          },
        },
        async (span) => {
          const max = this.opts.maxAttempts ?? 5;
          try {
            for (let attempt = 1; ; attempt++) {
              try {
                await reg.handle(event, envelope);
                return;
              } catch (e) {
                if (attempt >= max) throw e;
                const delay = Math.min(200 * 2 ** (attempt - 1), 5_000);
                this.log.warn(
                  `handler for ${envelope.type} failed (attempt ${attempt}/${max}): ${(e as Error).message}`,
                );
                await new Promise((r) => setTimeout(r, delay));
              }
            }
          } catch (e) {
            span.recordException(e as Error);
            span.setStatus({ code: SpanStatusCode.ERROR });
            this.log.error(`giving up on ${envelope.type} ${envelope.id}: ${(e as Error).message}`);
            await this.deadLetter(topic, message, e as Error);
          } finally {
            span.end();
          }
        },
      ),
    );
  }

  private async deadLetter(topic: string, message: KafkaJS.KafkaMessage, error: Error): Promise<void> {
    if (!this.producer) return;
    await this.producer.send({
      topic: `${topic}.dlq`,
      messages: [
        {
          key: message.key ?? null,
          value: message.value ?? null,
          headers: {
            ...(message.headers ?? {}),
            'x-dlq-error': error.message.slice(0, 1000),
            'x-dlq-consumer': this.opts.groupId,
            'x-dlq-offset': message.offset,
          },
        },
      ],
    });
  }
}

function headerOf(message: KafkaJS.KafkaMessage, name: string): string | undefined {
  const v = message.headers?.[name];
  if (v === undefined) return undefined;
  const first = Array.isArray(v) ? v[0] : v;
  return first === undefined ? undefined : first.toString();
}
