package com.congthanh.checkoutservice.adapter.out.persistence;

import com.congthanh.checkoutservice.application.port.out.SessionRepository;
import com.congthanh.checkoutservice.domain.exception.CheckoutException;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.Snapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.Optional;

/**
 * Phiên lưu dạng document JSONB (một dòng mỗi phiên), các cột tra cứu tách riêng.
 * Optimistic locking bằng cột version: hai request sửa cùng phiên thì request sau nhận CONFLICT.
 */
@Component
class JdbcSessionRepository implements SessionRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcSessionRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        // Bỏ qua field lạ khi đọc: document cũ vẫn đọc được sau khi đổi model, và getter phụ (isZero...) không cản.
        this.json = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    @Override
    public Optional<CheckoutSession> findById(String id) {
        return jdbc.sql("SELECT data::text AS data, version FROM checkout_sessions WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> {
                    Snapshot s = json.readValue(rs.getString("data"), Snapshot.class);
                    return CheckoutSession.restore(s.withVersion(rs.getLong("version")));
                })
                .optional();
    }

    @Override
    public CheckoutSession save(CheckoutSession session) {
        Snapshot s = session.snapshot();
        long next = s.version() + 1;
        String data = json.writeValueAsString(s.withVersion(next));
        int rows;
        if (s.version() == 0) {
            rows = jdbc.sql("""
                    INSERT INTO checkout_sessions (id, customer_id, status, order_id, order_code, data, expires_at, created_at, updated_at, version)
                    VALUES (:id, :customer, :status, :orderId, :orderCode, CAST(:data AS jsonb), :expires, :created, :updated, :version)
                    ON CONFLICT (id) DO NOTHING""")
                    .param("id", s.id()).param("customer", s.customerId()).param("status", s.status().name())
                    .param("orderId", s.orderId()).param("orderCode", s.orderCode()).param("data", data)
                    .param("expires", Timestamp.from(s.expiresAt())).param("created", Timestamp.from(s.createdAt()))
                    .param("updated", Timestamp.from(s.updatedAt())).param("version", next)
                    .update();
        } else {
            rows = jdbc.sql("""
                    UPDATE checkout_sessions SET status = :status, order_code = :orderCode, data = CAST(:data AS jsonb),
                        expires_at = :expires, updated_at = :updated, version = :next
                    WHERE id = :id AND version = :version""")
                    .param("id", s.id()).param("status", s.status().name()).param("orderCode", s.orderCode())
                    .param("data", data).param("expires", Timestamp.from(s.expiresAt()))
                    .param("updated", Timestamp.from(s.updatedAt())).param("next", next).param("version", s.version())
                    .update();
        }
        if (rows == 0) {
            throw CheckoutException.conflict("CONCURRENT_MODIFICATION", "the checkout session was modified concurrently");
        }
        return CheckoutSession.restore(s.withVersion(next));
    }
}
