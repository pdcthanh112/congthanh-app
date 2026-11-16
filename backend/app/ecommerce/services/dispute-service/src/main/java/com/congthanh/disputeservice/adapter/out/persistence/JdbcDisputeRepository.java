package com.congthanh.disputeservice.adapter.out.persistence;

import com.congthanh.disputeservice.application.port.out.DisputeRepository;
import com.congthanh.disputeservice.domain.event.DisputeEvent;
import com.congthanh.disputeservice.domain.model.Dispute;
import com.congthanh.disputeservice.domain.model.DisputeReason;
import com.congthanh.disputeservice.domain.model.DisputeStatus;
import com.congthanh.disputeservice.domain.model.Evidence;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.dispute.v1.DisputeClosed;
import com.congthanh.ecommerce.dispute.v1.DisputeEvidenceSubmitted;
import com.congthanh.ecommerce.dispute.v1.DisputeOpened;
import com.congthanh.platform.outbox.OutboxWriter;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class JdbcDisputeRepository implements DisputeRepository {

    static final String AGGREGATE = "dispute";
    private static final String COLUMNS = """
            id, provider, provider_dispute_id, payment_id, order_id, customer_id, amount, fee, currency, reason,
            evidence_due_by, status, evidence, note, created_at, updated_at, version""";

    private final JdbcClient jdbc;
    private final OutboxWriter outbox;
    private final ObjectMapper json;

    @Override
    public void save(Dispute d) {
        String evidence = d.evidence() == null ? null : json.writeValueAsString(d.evidence());
        int n;
        if (d.version() == 0) {
            n = jdbc.sql("""
                            INSERT INTO disputes (%s) VALUES (:id::bigint, :provider, :pid, :paymentId, :orderId, :customerId,
                                :amount, :fee, :currency, :reason, :due, :status, :evidence::jsonb, :note, :createdAt, :updatedAt, 1)
                            ON CONFLICT (provider, provider_dispute_id) DO NOTHING""".formatted(COLUMNS))
                    .param("id", d.id()).param("provider", d.provider()).param("pid", d.providerDisputeId())
                    .param("paymentId", d.paymentId()).param("orderId", d.orderId()).param("customerId", d.customerId())
                    .param("amount", d.amount()).param("fee", d.fee()).param("currency", d.currency())
                    .param("reason", d.reason().name()).param("due", ts(d.evidenceDueBy()))
                    .param("status", d.status().name()).param("evidence", evidence).param("note", d.note())
                    .param("createdAt", ts(d.createdAt())).param("updatedAt", ts(d.updatedAt()))
                    .update();
        } else {
            n = jdbc.sql("""
                            UPDATE disputes SET status = :status, evidence = :evidence::jsonb, note = :note,
                                   updated_at = :updatedAt, version = version + 1
                            WHERE id = :id::bigint AND version = :version""")
                    .param("id", d.id()).param("status", d.status().name()).param("evidence", evidence)
                    .param("note", d.note()).param("updatedAt", ts(d.updatedAt())).param("version", d.version())
                    .update();
        }
        if (n != 1) {
            throw new OptimisticLockingFailureException("dispute " + d.id() + " was modified concurrently");
        }
        for (DisputeEvent e : d.pullEvents()) {
            outbox.write(AGGREGATE, d.id(), toProto(e));
        }
    }

    @Override
    public Optional<Dispute> findById(String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM disputes WHERE id = :id::bigint").param("id", id)
                .query((rs, i) -> map(rs)).optional();
    }

    @Override
    public Optional<Dispute> findByProvider(String provider, String providerDisputeId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM disputes WHERE provider = :p AND provider_dispute_id = :pid FOR UPDATE")
                .param("p", provider).param("pid", providerDisputeId).query((rs, i) -> map(rs)).optional();
    }

    @Override
    public List<Dispute> byStatus(DisputeStatus status, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM disputes WHERE status = :s ORDER BY evidence_due_by NULLS LAST LIMIT :limit")
                .param("s", status.name()).param("limit", limit).query((rs, i) -> map(rs)).list();
    }

    @Override
    public List<Dispute> openDueBefore(Instant before, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM disputes WHERE status = 'NEEDS_RESPONSE' AND evidence_due_by < :t ORDER BY evidence_due_by LIMIT :limit")
                .param("t", ts(before)).param("limit", limit).query((rs, i) -> map(rs)).list();
    }

    private Dispute map(ResultSet rs) throws SQLException {
        String ev = rs.getString("evidence");
        Timestamp due = rs.getTimestamp("evidence_due_by");
        return Dispute.rehydrate(rs.getString("id"), rs.getString("provider"), rs.getString("provider_dispute_id"),
                rs.getString("payment_id"), rs.getString("order_id"), rs.getString("customer_id"),
                rs.getBigDecimal("amount"), rs.getBigDecimal("fee"), rs.getString("currency"),
                DisputeReason.valueOf(rs.getString("reason")), due == null ? null : due.toInstant(),
                DisputeStatus.valueOf(rs.getString("status")), ev == null ? null : json.readValue(ev, Evidence.class),
                rs.getString("note"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }

    static Message toProto(DisputeEvent e) {
        Dispute d = e.dispute();
        return switch (e) {
            case DisputeEvent.Opened o -> DisputeOpened.newBuilder().setDisputeId(d.id()).setProvider(d.provider())
                    .setProviderDisputeId(d.providerDisputeId()).setPaymentId(nz(d.paymentId())).setOrderId(nz(d.orderId()))
                    .setCustomerId(nz(d.customerId())).setAmount(money(d.amount(), d.currency()))
                    .setFee(money(d.fee(), d.currency())).setReason(d.reason().name())
                    .setEvidenceDueBy(d.evidenceDueBy() == null ? "" : d.evidenceDueBy().toString()).build();
            case DisputeEvent.EvidenceSubmitted s -> DisputeEvidenceSubmitted.newBuilder().setDisputeId(d.id())
                    .setOrderId(nz(d.orderId())).setSubmittedBy(nz(s.by())).build();
            case DisputeEvent.Closed c -> DisputeClosed.newBuilder().setDisputeId(d.id()).setProvider(d.provider())
                    .setOrderId(nz(d.orderId())).setCustomerId(nz(d.customerId())).setOutcome(d.status().name())
                    .setReason(d.reason().name()).setAmount(money(d.amount(), d.currency()))
                    .setFee(money(d.fee(), d.currency())).build();
        };
    }

    static Money money(BigDecimal amount, String currency) {
        if (currency == null || currency.isBlank()) {
            // Mọi khoản tiền phải mang đúng đơn vị của giao dịch; không đoán thay.
            throw new IllegalStateException("money amount " + amount + " has no currency");
        }
        BigDecimal a = amount.setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = a.setScale(0, RoundingMode.DOWN);
        return Money.newBuilder().setCurrencyCode(currency).setUnits(units.longValueExact())
                .setNanos(a.subtract(units).movePointRight(9).intValueExact()).build();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
