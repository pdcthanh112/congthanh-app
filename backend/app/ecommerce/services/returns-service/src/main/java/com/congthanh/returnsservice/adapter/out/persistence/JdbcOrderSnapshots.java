package com.congthanh.returnsservice.adapter.out.persistence;

import com.congthanh.returnsservice.application.port.out.OrderSnapshots;
import com.congthanh.returnsservice.domain.model.OrderSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class JdbcOrderSnapshots implements OrderSnapshots {

    private static final String COLUMNS = "order_id, customer_id, status, payment_id, currency, total, lines, delivered_at";

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    @Override
    public Optional<OrderSnapshot> find(String orderId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM order_snapshots WHERE order_id = :id AND customer_id IS NOT NULL")
                .param("id", orderId).query((rs, i) -> map(rs)).optional();
    }

    @Override
    public Optional<OrderSnapshot> lock(String orderId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM order_snapshots WHERE order_id = :id AND customer_id IS NOT NULL FOR UPDATE")
                .param("id", orderId).query((rs, i) -> map(rs)).optional();
    }

    @Override
    public void upsertPlaced(OrderSnapshot o) {
        jdbc.sql("""
                        INSERT INTO order_snapshots (order_id, customer_id, status, currency, total, lines)
                        VALUES (:id, :customer, 'PLACED', :currency, :total, :lines::jsonb)
                        ON CONFLICT (order_id) DO UPDATE SET customer_id = EXCLUDED.customer_id,
                            currency = EXCLUDED.currency, total = EXCLUDED.total, lines = EXCLUDED.lines,
                            status = COALESCE(order_snapshots.status, 'PLACED'), updated_at = now()""")
                .param("id", o.orderId()).param("customer", o.customerId()).param("currency", o.currency())
                .param("total", o.total()).param("lines", json.writeValueAsString(o.lines()))
                .update();
    }

    @Override
    public void markStatus(String orderId, String status, String paymentId) {
        jdbc.sql("""
                        INSERT INTO order_snapshots (order_id, status, payment_id) VALUES (:id, :status, :payment)
                        ON CONFLICT (order_id) DO UPDATE SET status = EXCLUDED.status,
                            payment_id = COALESCE(EXCLUDED.payment_id, order_snapshots.payment_id), updated_at = now()""")
                .param("id", orderId).param("status", status).param("payment", paymentId).update();
    }

    @Override
    public void markDelivered(String orderId, Instant at) {
        jdbc.sql("""
                        INSERT INTO order_snapshots (order_id, delivered_at) VALUES (:id, :at)
                        ON CONFLICT (order_id) DO UPDATE
                        SET delivered_at = COALESCE(order_snapshots.delivered_at, EXCLUDED.delivered_at), updated_at = now()""")
                .param("id", orderId).param("at", Timestamp.from(at)).update();
    }

    private OrderSnapshot map(ResultSet rs) throws SQLException {
        String rawLines = rs.getString("lines");
        List<OrderSnapshot.Line> lines = rawLines == null ? List.of()
                : json.readValue(rawLines, new TypeReference<List<OrderSnapshot.Line>>() {
        });
        Timestamp delivered = rs.getTimestamp("delivered_at");
        return new OrderSnapshot(rs.getString("order_id"), rs.getString("customer_id"), rs.getString("status"),
                rs.getString("payment_id"), rs.getString("currency"), rs.getBigDecimal("total"), lines,
                delivered == null ? null : delivered.toInstant());
    }
}
