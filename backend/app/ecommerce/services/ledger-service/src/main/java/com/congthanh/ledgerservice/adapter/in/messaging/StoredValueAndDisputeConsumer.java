package com.congthanh.ledgerservice.adapter.in.messaging;

import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.dispute.v1.DisputeClosed;
import com.congthanh.ecommerce.dispute.v1.DisputeOpened;
import com.congthanh.ecommerce.loyalty.v1.PointsConfirmed;
import com.congthanh.ecommerce.loyalty.v1.PointsExpired;
import com.congthanh.ecommerce.loyalty.v1.PointsRedeemed;
import com.congthanh.ecommerce.loyalty.v1.PointsReversed;
import com.congthanh.ecommerce.reconciliation.v1.ReconciliationRunCompleted;
import com.congthanh.ecommerce.storedvalue.v1.StoredValueCredited;
import com.congthanh.ecommerce.storedvalue.v1.StoredValueDebited;
import com.congthanh.ledgerservice.application.port.in.LedgerPosting;
import com.congthanh.ledgerservice.domain.exception.LedgerException;
import com.congthanh.ledgerservice.domain.posting.FinancialFact;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

import static com.congthanh.ledgerservice.adapter.in.messaging.PaymentEventsConsumer.amount;

/** Hạch toán store credit (stored-value-service), tranh chấp (dispute-service) và điểm thưởng (loyalty-service). Cách xử lý lỗi như PaymentEventsConsumer. */
@Component
@RequiredArgsConstructor
class StoredValueAndDisputeConsumer {

    private final LedgerPosting ledger;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    @KafkaListener(topics = {"ecommerce.storedvalue.events", "ecommerce.dispute.events", "ecommerce.loyalty.events",
            "ecommerce.reconciliation.events"},
            groupId = PaymentEventsConsumer.CONSUMER)
    void onEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        Optional<FinancialFact> fact;
        try {
            fact = toFact(env);
        } catch (LedgerException | IllegalArgumentException e) {
            tx.executeWithoutResult(s -> ledger.recordUnposted(env.getId(), env.getType(), e.getMessage()));
            return;
        }
        fact.ifPresent(f -> tx.executeWithoutResult(s -> {
            if (!processed.markProcessed(env.getId(), PaymentEventsConsumer.CONSUMER)) {
                return;
            }
            try {
                ledger.post(f);
            } catch (LedgerException e) {
                ledger.recordUnposted(env.getId(), env.getType(), e.getMessage());
            }
        }));
    }

    static Optional<FinancialFact> toFact(EventEnvelope env) {
        Instant at = env.hasOccurredAt()
                ? Instant.ofEpochSecond(env.getOccurredAt().getSeconds(), env.getOccurredAt().getNanos())
                : null;
        Optional<FinancialFact> f = EventEnvelopes.unpack(env, StoredValueCredited.class)
                .map(c -> new FinancialFact.StoredValueCredited(env.getId(), at, c.getAccountId(), c.getKind(),
                        c.getReason(), c.getReference(), amount(c.getAmount()), c.getAmount().getCurrencyCode()));
        if (f.isEmpty()) {
            f = EventEnvelopes.unpack(env, StoredValueDebited.class)
                    .map(d -> new FinancialFact.StoredValueDebited(env.getId(), at, d.getAccountId(), d.getKind(),
                            d.getHoldId(), d.getOrderId(), amount(d.getAmount()), d.getAmount().getCurrencyCode()));
        }
        if (f.isEmpty()) {
            f = EventEnvelopes.unpack(env, DisputeOpened.class)
                    .map(o -> new FinancialFact.DisputeOpened(env.getId(), at, o.getDisputeId(), o.getProvider(),
                            o.getOrderId(), amount(o.getAmount()), amount(o.getFee()), o.getAmount().getCurrencyCode()));
        }
        if (f.isEmpty()) {
            f = EventEnvelopes.unpack(env, DisputeClosed.class)
                    .map(c -> new FinancialFact.DisputeClosed(env.getId(), at, c.getDisputeId(), c.getProvider(),
                            c.getOutcome(), amount(c.getAmount()), c.getAmount().getCurrencyCode()));
        }
        if (f.isEmpty()) {
            f = loyalty(env, at);
        }
        if (f.isEmpty()) {
            f = EventEnvelopes.unpack(env, ReconciliationRunCompleted.class)
                    .filter(r -> r.hasProviderFees() && (r.getProviderFees().getUnits() != 0 || r.getProviderFees().getNanos() != 0))
                    .map(r -> new FinancialFact.ProviderFees(env.getId(), at, r.getProvider(), r.getBusinessDate(),
                            amount(r.getProviderFees()), null, r.getProviderFees().getCurrencyCode()));
        }
        return f;
    }

    static Optional<FinancialFact> loyalty(EventEnvelope env, Instant at) {
        Optional<FinancialFact> f = EventEnvelopes.unpack(env, PointsConfirmed.class)
                .map(p -> points(env, at, "CONFIRMED", p.getCustomerId(), p.getLotId(), p.getPoints(), p.getValue()));
        if (f.isEmpty()) {
            f = EventEnvelopes.unpack(env, PointsRedeemed.class)
                    .map(p -> points(env, at, "REDEEMED", p.getCustomerId(), p.getRedemptionId(), p.getPoints(), p.getValue()));
        }
        if (f.isEmpty()) {
            // điểm còn chờ chưa từng vào sổ nên đảo không cần bút toán
            f = EventEnvelopes.unpack(env, PointsReversed.class).filter(PointsReversed::getConfirmed)
                    .map(p -> points(env, at, "REVERSED", p.getCustomerId(), p.getOrderId(), p.getPoints(), p.getValue()));
        }
        if (f.isEmpty()) {
            f = EventEnvelopes.unpack(env, PointsExpired.class)
                    .map(p -> points(env, at, "EXPIRED", p.getCustomerId(), p.getLotId(), p.getPoints(), p.getValue()));
        }
        return f.filter(x -> ((FinancialFact.LoyaltyPoints) x).value().signum() > 0);
    }

    private static FinancialFact points(EventEnvelope env, Instant at, String kind, String customer, String ref, long points,
                                        com.congthanh.ecommerce.common.v1.Money value) {
        return new FinancialFact.LoyaltyPoints(env.getId(), at, kind, customer, ref, points, amount(value),
                value.getCurrencyCode().isEmpty() ? "USD" : value.getCurrencyCode());
    }
}
