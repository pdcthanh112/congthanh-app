package com.congthanh.returnsservice.adapter.out.persistence;

import com.congthanh.returnsservice.application.port.out.ReturnRepository;
import com.congthanh.returnsservice.domain.event.ReturnEvent;
import com.congthanh.returnsservice.domain.model.ItemCondition;
import com.congthanh.returnsservice.domain.model.RefundMethod;
import com.congthanh.returnsservice.domain.model.ReturnLine;
import com.congthanh.returnsservice.domain.model.ReturnReason;
import com.congthanh.returnsservice.domain.model.ReturnRequest;
import com.congthanh.returnsservice.domain.model.ReturnStatus;
import com.congthanh.platform.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class JdbcReturnRepository implements ReturnRepository {

    static final String AGGREGATE = "returns";
    private static final String COLUMNS = """
            id, order_id, customer_id, rma_code, currency, refund_method, lines, status, refund_amount, restocking_fee, payment_id,
            note, created_at, updated_at, version""";

    private final JdbcClient jdbc;
    private final OutboxWriter outbox;
    private final ObjectMapper json;

    @Override
    public void save(ReturnRequest r) {
        String lines = json.writeValueAsString(r.lines().stream().map(LineJson::of).toList());
        int n;
        if (r.version() == 0 && findById(r.id()).isEmpty()) {
            n = jdbc.sql("""
                            INSERT INTO return_requests (%s)
                            VALUES (:id::bigint, :orderId, :customerId, :rma, :currency, :method, :lines::jsonb, :status, :refund, :fee,
                                    :paymentId, :note, :createdAt, :updatedAt, 1)""".formatted(COLUMNS))
                    .param("id", r.id()).param("orderId", r.orderId()).param("customerId", r.customerId())
                    .param("rma", r.rmaCode()).param("currency", r.currency()).param("method", r.refundMethod().name())
                    .param("lines", lines)
                    .param("status", r.status().name()).param("refund", r.refundAmount()).param("fee", r.restockingFee())
                    .param("paymentId", r.paymentId()).param("note", r.note())
                    .param("createdAt", Timestamp.from(r.createdAt())).param("updatedAt", Timestamp.from(r.updatedAt()))
                    .update();
        } else {
            n = jdbc.sql("""
                            UPDATE return_requests SET lines = :lines::jsonb, status = :status, refund_amount = :refund,
                                   restocking_fee = :fee, payment_id = :paymentId, note = :note, updated_at = :updatedAt,
                                   version = version + 1
                            WHERE id = :id::bigint AND version = :version""")
                    .param("id", r.id()).param("lines", lines).param("status", r.status().name())
                    .param("refund", r.refundAmount()).param("fee", r.restockingFee()).param("paymentId", r.paymentId())
                    .param("note", r.note()).param("updatedAt", Timestamp.from(r.updatedAt()))
                    .param("version", r.version())
                    .update();
        }
        if (n != 1) {
            throw new OptimisticLockingFailureException("return " + r.id() + " was modified concurrently");
        }
        for (ReturnEvent e : r.pullEvents()) {
            outbox.write(AGGREGATE, r.id(), ReturnEventMapper.toProto(e));
        }
    }

    @Override
    public Optional<ReturnRequest> findById(String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM return_requests WHERE id = :id::bigint")
                .param("id", id).query((rs, i) -> map(rs)).optional();
    }

    @Override
    public List<ReturnRequest> byCustomer(String customerId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM return_requests WHERE customer_id = :c ORDER BY created_at DESC LIMIT :limit")
                .param("c", customerId).param("limit", limit).query((rs, i) -> map(rs)).list();
    }

    @Override
    public List<ReturnRequest> byStatus(ReturnStatus status, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM return_requests WHERE status = :s ORDER BY created_at LIMIT :limit")
                .param("s", status.name()).param("limit", limit).query((rs, i) -> map(rs)).list();
    }

    @Override
    public List<ReturnRequest> stuck(ReturnStatus status, Instant updatedBefore, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM return_requests WHERE status = :s AND updated_at < :t ORDER BY updated_at LIMIT :limit")
                .param("s", status.name()).param("t", Timestamp.from(updatedBefore)).param("limit", limit)
                .query((rs, i) -> map(rs)).list();
    }

    @Override
    public Map<String, Integer> openQuantities(String orderId) {
        Map<String, Integer> out = new HashMap<>();
        jdbc.sql("""
                        SELECT l->>'productId', SUM((l->>'quantity')::int)
                        FROM return_requests r, jsonb_array_elements(r.lines) l
                        WHERE r.order_id = :o AND r.status NOT IN ('REJECTED', 'CANCELLED')
                        GROUP BY 1""")
                .param("o", orderId)
                .query(rs -> {
                    out.put(rs.getString(1), rs.getInt(2));
                });
        return out;
    }

    @Override
    public BigDecimal refundCommitted(String orderId, String exceptReturnId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(refund_amount), 0) FROM return_requests
                        WHERE order_id = :o AND id <> :id::bigint AND status IN ('REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED')""")
                .param("o", orderId).param("id", exceptReturnId).query(BigDecimal.class).single();
    }

    private ReturnRequest map(ResultSet rs) throws SQLException {
        List<LineJson> raw = json.readValue(rs.getString("lines"), new TypeReference<>() {
        });
        return ReturnRequest.rehydrate(rs.getString("id"), rs.getString("order_id"), rs.getString("customer_id"),
                rs.getString("rma_code"), rs.getString("currency"),
                RefundMethod.valueOf(rs.getString("refund_method")), raw.stream().map(LineJson::toDomain).toList(),
                ReturnStatus.valueOf(rs.getString("status")), rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("restocking_fee"), rs.getString("payment_id"), rs.getString("note"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }

    record LineJson(String productId, int quantity, String reason, BigDecimal unitPrice, String condition) {
        static LineJson of(ReturnLine l) {
            return new LineJson(l.productId(), l.quantity(), l.reason().name(), l.unitPrice(),
                    l.condition() == null ? null : l.condition().name());
        }

        ReturnLine toDomain() {
            return new ReturnLine(productId, quantity, ReturnReason.valueOf(reason), unitPrice,
                    condition == null ? null : ItemCondition.valueOf(condition));
        }
    }
}
