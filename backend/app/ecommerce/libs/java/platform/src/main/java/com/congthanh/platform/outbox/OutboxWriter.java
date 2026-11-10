package com.congthanh.platform.outbox;

import com.congthanh.ecom.common.v1.EventEnvelope;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Ghi event vào bảng {@code outbox} trong CÙNG transaction với dữ liệu nghiệp vụ.
 * Debezium (Outbox Event Router) đọc WAL và đẩy lên Kafka, nên không còn dual-write DB + Kafka.
 *
 * <p>Payload là {@code ecommerce.common.v1.EventEnvelope} (protobuf) giống hệt service Go, nên consumer
 * không cần biết event đến từ ngôn ngữ nào.
 */
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final String source;
    private final Clock clock;

    public OutboxWriter(JdbcClient jdbc, String source, Clock clock) {
        this.jdbc = jdbc;
        this.source = source;
        this.clock = clock;
    }

    /** Bắt buộc có transaction đang chạy: outbox không được ghi lẻ ngoài transaction nghiệp vụ. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID write(String aggregateType, String aggregateId, Message payload) {
        String traceparent = traceparent();
        EventEnvelope envelope = envelope(source, aggregateType, aggregateId, payload, traceparent, clock.instant());
        UUID id = UUID.fromString(envelope.getId());
        jdbc.sql("""
                        INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload, traceparent)
                        VALUES (:id, :aggregateType, :aggregateId, :type, :payload, :traceparent)""")
                .param("id", id)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("type", envelope.getType())
                .param("payload", envelope.toByteArray())
                .param("traceparent", traceparent)
                .update();
        return id;
    }

    public static EventEnvelope envelope(String source, String aggregateType, String aggregateId,
                                         Message payload, String traceparent, Instant at) {
        return EventEnvelope.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setType(typeName(payload))
                .setSource(source)
                .setAggregateType(aggregateType)
                .setAggregateId(aggregateId)
                .setOccurredAt(Timestamp.newBuilder().setSeconds(at.getEpochSecond()).setNanos(at.getNano()))
                .setTraceparent(traceparent == null ? "" : traceparent)
                .setPayload(Any.pack(payload))
                .build();
    }

    /** Tên đầy đủ của event, ví dụ {@code ecommerce.order.v1.OrderPlaced}; cũng là header {@code ce_type}. */
    public static String typeName(Message payload) {
        return payload.getDescriptorForType().getFullName();
    }

    /** W3C traceparent của span hiện tại, để trace nối tiếp qua Kafka sang consumer. */
    static String traceparent() {
        SpanContext ctx = Span.current().getSpanContext();
        if (!ctx.isValid()) {
            return null;
        }
        return "00-" + ctx.getTraceId() + "-" + ctx.getSpanId() + "-" + ctx.getTraceFlags().asHex();
    }
}
