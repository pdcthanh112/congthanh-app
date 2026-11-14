package com.congthanh.disputeservice.application.service;

import com.congthanh.disputeservice.application.port.in.DisputeDesk;
import com.congthanh.disputeservice.application.port.in.DisputeIntake;
import com.congthanh.disputeservice.application.port.in.FactsProjection;
import com.congthanh.disputeservice.application.port.out.DisputeRepository;
import com.congthanh.disputeservice.application.port.out.FactsStore;
import com.congthanh.disputeservice.application.port.out.Ids;
import com.congthanh.disputeservice.application.port.out.ProviderDisputes;
import com.congthanh.disputeservice.domain.exception.DisputeException;
import com.congthanh.disputeservice.domain.model.Dispute;
import com.congthanh.disputeservice.domain.model.DisputeReason;
import com.congthanh.disputeservice.domain.model.DisputeStatus;
import com.congthanh.disputeservice.domain.model.Evidence;
import com.congthanh.disputeservice.domain.model.EvidenceAssembler;
import com.congthanh.disputeservice.domain.model.OrderFacts;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisputeService implements DisputeIntake, DisputeDesk, FactsProjection {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);
    static final Duration DUE_SOON = Duration.ofDays(3);

    private final DisputeRepository disputes;
    private final FactsStore facts;
    private final ProviderDisputes providers;
    private final Ids ids;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Value("${disputes.refund-policy:Returns accepted within 30 days of delivery; refunds go to the original payment method.}")
    private String refundPolicy;

    @Override
    public Dispute receive(ProviderDispute p) {
        return tx.execute(s -> {
            Instant now = clock.instant();
            Dispute d = disputes.findByProvider(p.provider(), p.providerDisputeId()).orElse(null);
            if (d == null) {
                var ref = facts.paymentByRef(p.provider(), p.providerPaymentRef());
                if (ref.isEmpty()) {
                    log.warn("dispute {} for unknown {} payment {}", p.providerDisputeId(), p.provider(), p.providerPaymentRef());
                }
                d = Dispute.open(ids.next(), p.provider(), p.providerDisputeId(),
                        ref.map(FactsStore.PaymentRef::paymentId).orElse(null),
                        ref.map(FactsStore.PaymentRef::orderId).orElse(null),
                        ref.map(FactsStore.PaymentRef::customerId).orElse(null),
                        p.amount(), p.fee(), p.currency(), DisputeReason.fromProvider(p.reasonCode()), p.evidenceDueBy(), now);
                OrderFacts f = d.orderId() == null ? null : facts.orderFacts(d.orderId()).orElse(null);
                d.attachEvidence(EvidenceAssembler.assemble(d, f, refundPolicy), now);
            }
            String status = p.providerStatus() == null ? "" : p.providerStatus().toLowerCase(Locale.ROOT);
            if (status.equals("won") || status.equals("warning_closed")) {
                d.closed(true, now);
            } else if (status.equals("lost")) {
                d.closed(false, now);
            }
            disputes.save(d);
            return d;
        });
    }

    @Override
    public List<Dispute> byStatus(DisputeStatus status, int limit) {
        return disputes.byStatus(status, Math.clamp(limit, 1, 500));
    }

    @Override
    public Dispute get(String id) {
        return disputes.findById(id).orElseThrow(() -> DisputeException.notFound("dispute " + id));
    }

    @Override
    public Dispute refreshEvidence(String id) {
        return tx.execute(s -> {
            Dispute d = get(id);
            OrderFacts f = d.orderId() == null ? null : facts.orderFacts(d.orderId()).orElse(null);
            d.attachEvidence(EvidenceAssembler.assemble(d, f, refundPolicy), clock.instant());
            disputes.save(d);
            return d;
        });
    }

    @Override
    public Dispute submit(String id, Map<String, String> extraFields, String by) {
        Dispute d = get(id);
        if (d.status() == DisputeStatus.SUBMITTED) {
            return d;
        }
        Evidence e = d.evidence();
        Map<String, String> fields = new LinkedHashMap<>(e == null ? Map.of() : e.fields());
        if (extraFields != null) {
            extraFields.forEach((k, v) -> {
                if (v == null || v.isBlank()) {
                    fields.remove(k);
                } else {
                    fields.put(k, v);
                }
            });
        }
        Evidence finalEvidence = new Evidence(fields, e == null ? Evidence.Recommendation.REVIEW : e.recommendation(),
                e == null ? "" : e.rationale());
        // Kiểm trạng thái/hạn trước khi gọi cổng, rồi mới gửi và ghi lại.
        d.attachEvidence(finalEvidence, clock.instant());
        providers.submitEvidence(d, fields);
        return tx.execute(s -> {
            Dispute fresh = get(id);
            fresh.attachEvidence(finalEvidence, clock.instant());
            fresh.submitted(by, clock.instant());
            disputes.save(fresh);
            return fresh;
        });
    }

    @Override
    public Dispute accept(String id, String why, String by) {
        Dispute d = get(id);
        if (d.status() != DisputeStatus.NEEDS_RESPONSE) {
            throw DisputeException.conflict("dispute " + id + " is " + d.status());
        }
        providers.accept(d);
        return tx.execute(s -> {
            Dispute fresh = get(id);
            fresh.accepted(by, why, clock.instant());
            disputes.save(fresh);
            return fresh;
        });
    }

    @Override
    public List<Dispute> dueSoon() {
        return disputes.openDueBefore(clock.instant().plus(DUE_SOON), 200);
    }

    @Override
    public void orderPlaced(String orderId, String customerId, String recipientName, String region, String postalCode,
                            List<String> productIds, BigDecimal total) {
        facts.upsertOrder(orderId, customerId, recipientName, region, postalCode, productIds, total);
    }

    @Override
    public void paymentCaptured(String paymentId, String orderId, String customerId, String provider, String providerRef) {
        if (providerRef == null || providerRef.isBlank()) {
            return;
        }
        facts.upsertPayment(new FactsStore.PaymentRef(paymentId, orderId, customerId, provider, providerRef));
    }

    @Override
    public void shipmentStatus(String orderId, String status, String carrier, String trackingNumber, Instant at) {
        facts.upsertShipment(orderId, status, carrier, trackingNumber, at);
    }

    @Override
    public void refunded(String orderId, String refundKey, BigDecimal amount, Instant at) {
        facts.addRefund(orderId, refundKey, amount, at);
    }
}
