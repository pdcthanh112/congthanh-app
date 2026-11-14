package com.congthanh.disputeservice.adapter.out.persistence;

import com.congthanh.disputeservice.application.port.out.FactsStore;
import com.congthanh.disputeservice.domain.model.OrderFacts;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bảng chiếu dữ kiện đơn. Event đến lệch thứ tự giữa các topic nên mọi lệnh là upsert không ghi đè bằng rỗng. */
@Component
@RequiredArgsConstructor
class JdbcFactsStore implements FactsStore {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    @Override
    public Optional<OrderFacts> orderFacts(String orderId) {
        return jdbc.sql("""
                        SELECT o.order_id, o.customer_id, o.recipient_name, o.region, o.postal_code, o.product_ids, o.total,
                               o.carrier, o.tracking_number, o.shipped_at, o.delivered_at,
                               COALESCE((SELECT SUM(amount) FROM order_refunds r WHERE r.order_id = o.order_id), 0),
                               (SELECT MAX(at) FROM order_refunds r WHERE r.order_id = o.order_id)
                        FROM order_facts o WHERE o.order_id = :id""")
                .param("id", orderId)
                .query((rs, i) -> {
                    String ids = rs.getString(6);
                    List<String> productIds = ids == null ? List.of() : json.readValue(ids, new TypeReference<List<String>>() {
                    });
                    return new OrderFacts(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), productIds, rs.getBigDecimal(7), rs.getString(8), rs.getString(9),
                            instant(rs.getTimestamp(10)), instant(rs.getTimestamp(11)), rs.getBigDecimal(12),
                            instant(rs.getTimestamp(13)));
                })
                .optional();
    }

    @Override
    public Optional<PaymentRef> paymentByRef(String provider, String providerRef) {
        if (providerRef == null || providerRef.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT payment_id, order_id, customer_id, provider, provider_ref FROM payment_refs WHERE provider = :p AND provider_ref = :ref")
                .param("p", provider).param("ref", providerRef)
                .query((rs, i) -> new PaymentRef(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)))
                .optional();
    }

    @Override
    public void upsertOrder(String orderId, String customerId, String recipientName, String region, String postalCode,
                            List<String> productIds, BigDecimal total) {
        jdbc.sql("""
                        INSERT INTO order_facts (order_id, customer_id, recipient_name, region, postal_code, product_ids, total)
                        VALUES (:id, :c, :name, :region, :zip, :ids::jsonb, :total)
                        ON CONFLICT (order_id) DO UPDATE SET customer_id = EXCLUDED.customer_id,
                            recipient_name = EXCLUDED.recipient_name, region = EXCLUDED.region,
                            postal_code = EXCLUDED.postal_code, product_ids = EXCLUDED.product_ids, total = EXCLUDED.total""")
                .param("id", orderId).param("c", customerId).param("name", recipientName).param("region", region)
                .param("zip", postalCode).param("ids", json.writeValueAsString(productIds)).param("total", total)
                .update();
    }

    @Override
    public void upsertPayment(PaymentRef ref) {
        jdbc.sql("""
                        INSERT INTO payment_refs (provider, provider_ref, payment_id, order_id, customer_id)
                        VALUES (:p, :ref, :pay, :o, :c) ON CONFLICT (provider, provider_ref) DO NOTHING""")
                .param("p", ref.provider()).param("ref", ref.providerRef()).param("pay", ref.paymentId())
                .param("o", ref.orderId()).param("c", ref.customerId()).update();
    }

    @Override
    public void upsertShipment(String orderId, String status, String carrier, String trackingNumber, Instant at) {
        boolean delivered = "DELIVERED".equalsIgnoreCase(status);
        boolean shipped = !delivered && status != null && !status.isBlank() && !"CREATED".equalsIgnoreCase(status)
                && !"PENDING".equalsIgnoreCase(status);
        jdbc.sql("""
                        INSERT INTO order_facts (order_id, carrier, tracking_number, shipped_at, delivered_at)
                        VALUES (:id, :carrier, :tracking, :shipped, :delivered)
                        ON CONFLICT (order_id) DO UPDATE SET
                            carrier = COALESCE(NULLIF(EXCLUDED.carrier, ''), order_facts.carrier),
                            tracking_number = COALESCE(NULLIF(EXCLUDED.tracking_number, ''), order_facts.tracking_number),
                            shipped_at = COALESCE(order_facts.shipped_at, EXCLUDED.shipped_at),
                            delivered_at = COALESCE(order_facts.delivered_at, EXCLUDED.delivered_at)""")
                .param("id", orderId).param("carrier", carrier).param("tracking", trackingNumber)
                .param("shipped", shipped || delivered ? Timestamp.from(at) : null)
                .param("delivered", delivered ? Timestamp.from(at) : null)
                .update();
    }

    @Override
    public void addRefund(String orderId, String refundKey, BigDecimal amount, Instant at) {
        jdbc.sql("""
                        INSERT INTO order_refunds (refund_key, order_id, amount, at) VALUES (:k, :o, :a, :at)
                        ON CONFLICT (refund_key) DO NOTHING""")
                .param("k", refundKey).param("o", orderId).param("a", amount).param("at", Timestamp.from(at)).update();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
