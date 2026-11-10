package com.congthanh.platform.inbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer: Kafka giao ít nhất một lần, nên consumer đánh dấu event đã xử lý trong
 * CÙNG transaction với tác dụng phụ. Lần giao lại sẽ thấy {@code false} và bỏ qua.
 */
public class ProcessedEvents {

    private final JdbcClient jdbc;

    public ProcessedEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true nếu đây là lần đầu consumer này thấy event; false nếu đã xử lý trước đó. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String eventId, String consumer) {
        return jdbc.sql("""
                        INSERT INTO processed_events (event_id, consumer) VALUES (:id, :consumer)
                        ON CONFLICT DO NOTHING""")
                .param("id", eventId)
                .param("consumer", consumer)
                .update() == 1;
    }
}
