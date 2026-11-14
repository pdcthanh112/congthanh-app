package com.congthanh.disputeservice.domain.model;

import com.congthanh.disputeservice.domain.event.DisputeEvent;
import com.congthanh.disputeservice.domain.exception.DisputeException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Một tranh chấp (chargeback). Vòng đời NEEDS_RESPONSE → SUBMITTED → WON/LOST, hoặc ACCEPTED (không chống).
 * Hạn nộp bằng chứng là cứng: quá hạn thì cổng tự xử thua.
 */
public final class Dispute {

    private final String id;
    private final String provider;
    private final String providerDisputeId;
    private final String paymentId;
    private final String orderId;
    private final String customerId;
    private final BigDecimal amount;
    private final BigDecimal fee;
    private final String currency;
    private final DisputeReason reason;
    private final Instant evidenceDueBy;
    private final Instant createdAt;
    private final long version;

    private DisputeStatus status;
    private Evidence evidence;
    private String note;
    private Instant updatedAt;
    private final List<DisputeEvent> events = new ArrayList<>();

    private Dispute(String id, String provider, String providerDisputeId, String paymentId, String orderId,
                    String customerId, BigDecimal amount, BigDecimal fee, String currency, DisputeReason reason,
                    Instant evidenceDueBy, DisputeStatus status, Evidence evidence, String note, Instant createdAt,
                    Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.provider = Objects.requireNonNull(provider);
        this.providerDisputeId = Objects.requireNonNull(providerDisputeId);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = Objects.requireNonNull(amount);
        this.fee = fee == null ? BigDecimal.ZERO : fee;
        this.currency = currency;
        this.reason = reason;
        this.evidenceDueBy = evidenceDueBy;
        this.status = status;
        this.evidence = evidence;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Dispute open(String id, String provider, String providerDisputeId, String paymentId, String orderId,
                               String customerId, BigDecimal amount, BigDecimal fee, String currency,
                               DisputeReason reason, Instant evidenceDueBy, Instant now) {
        if (amount == null || amount.signum() <= 0) {
            throw DisputeException.invalid("dispute amount must be positive");
        }
        Dispute d = new Dispute(id, provider, providerDisputeId, paymentId, orderId, customerId, amount, fee, currency,
                reason, evidenceDueBy, DisputeStatus.NEEDS_RESPONSE, null, null, now, now, 0);
        d.events.add(new DisputeEvent.Opened(d));
        return d;
    }

    public static Dispute rehydrate(String id, String provider, String providerDisputeId, String paymentId,
                                    String orderId, String customerId, BigDecimal amount, BigDecimal fee,
                                    String currency, DisputeReason reason, Instant evidenceDueBy, DisputeStatus status,
                                    Evidence evidence, String note, Instant createdAt, Instant updatedAt, long version) {
        return new Dispute(id, provider, providerDisputeId, paymentId, orderId, customerId, amount, fee, currency,
                reason, evidenceDueBy, status, evidence, note, createdAt, updatedAt, version);
    }

    public void attachEvidence(Evidence e, Instant now) {
        requireOpen("attach evidence");
        this.evidence = e;
        this.updatedAt = now;
    }

    /** Người xử lý duyệt và nộp. Không cho nộp quá hạn (cổng sẽ từ chối). */
    public void submitted(String by, Instant now) {
        if (status == DisputeStatus.SUBMITTED) {
            return;
        }
        requireOpen("submit evidence");
        if (evidence == null) {
            throw DisputeException.conflict("dispute " + id + " has no evidence to submit");
        }
        if (evidenceDueBy != null && now.isAfter(evidenceDueBy)) {
            throw DisputeException.conflict("evidence for dispute " + id + " was due " + evidenceDueBy);
        }
        status = DisputeStatus.SUBMITTED;
        updatedAt = now;
        events.add(new DisputeEvent.EvidenceSubmitted(this, by));
    }

    /** Quyết định không chống (thường khi bằng chứng yếu hoặc số tiền nhỏ). */
    public void accepted(String by, String why, Instant now) {
        if (status == DisputeStatus.ACCEPTED) {
            return;
        }
        requireOpen("accept");
        status = DisputeStatus.ACCEPTED;
        note = why;
        updatedAt = now;
        events.add(new DisputeEvent.Closed(this));
    }

    /** Kết quả cuối do cổng báo. Idempotent. */
    public void closed(boolean won, Instant now) {
        DisputeStatus target = won ? DisputeStatus.WON : DisputeStatus.LOST;
        if (status == target || status == DisputeStatus.ACCEPTED) {
            return;
        }
        if (status.closed()) {
            throw DisputeException.conflict("dispute " + id + " already closed as " + status);
        }
        status = target;
        updatedAt = now;
        events.add(new DisputeEvent.Closed(this));
    }

    /** Còn mở và hạn nộp nằm trong khoảng {@code within}. */
    public boolean dueSoon(Instant now, Duration within) {
        return status == DisputeStatus.NEEDS_RESPONSE && evidenceDueBy != null && !now.plus(within).isBefore(evidenceDueBy);
    }

    public List<DisputeEvent> pullEvents() {
        List<DisputeEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    private void requireOpen(String action) {
        if (status != DisputeStatus.NEEDS_RESPONSE) {
            throw DisputeException.conflict("cannot " + action + " for dispute " + id + " in status " + status);
        }
    }

    public String id() { return id; }
    public String provider() { return provider; }
    public String providerDisputeId() { return providerDisputeId; }
    public String paymentId() { return paymentId; }
    public String orderId() { return orderId; }
    public String customerId() { return customerId; }
    public BigDecimal amount() { return amount; }
    public BigDecimal fee() { return fee; }
    public String currency() { return currency; }
    public DisputeReason reason() { return reason; }
    public Instant evidenceDueBy() { return evidenceDueBy; }
    public DisputeStatus status() { return status; }
    public Evidence evidence() { return evidence; }
    public String note() { return note; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
