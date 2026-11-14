package com.congthanh.ledgerservice.adapter.in.messaging;

import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.payment.v1.PaymentCaptured;
import com.congthanh.ecommerce.payment.v1.PaymentRefunded;
import com.congthanh.ledgerservice.application.port.in.LedgerPosting;
import com.congthanh.ledgerservice.domain.exception.LedgerException;
import com.congthanh.ledgerservice.domain.posting.FinancialFact;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Dịch event thanh toán thành sự kiện tài chính rồi hạch toán. Event lặp (Kafka giao lại) bị bỏ qua nhờ
 * processed_events và ràng buộc UNIQUE source_event_id. Event không hợp lệ ghi vào unposted_events
 * thay vì làm kẹt partition.
 */
@Component
@RequiredArgsConstructor
class PaymentEventsConsumer {

    static final String CONSUMER = "ledger-service";
    private static final Logger log = LoggerFactory.getLogger(PaymentEventsConsumer.class);

    private final LedgerPosting ledger;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    @KafkaListener(topics = "ecommerce.payment.events", groupId = CONSUMER)
    void onPaymentEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        Optional<FinancialFact> fact;
        try {
            fact = toFact(env);
        } catch (LedgerException | IllegalArgumentException e) {
            tx.executeWithoutResult(s -> ledger.recordUnposted(env.getId(), env.getType(), e.getMessage()));
            return;
        }
        fact.ifPresent(f -> tx.executeWithoutResult(s -> {
            if (!processed.markProcessed(env.getId(), CONSUMER)) {
                return;
            }
            try {
                ledger.post(f);
            } catch (LedgerException e) {
                log.warn("cannot post event {} ({}): {}", env.getId(), env.getType(), e.getMessage());
                ledger.recordUnposted(env.getId(), env.getType(), e.getMessage());
            }
        }));
    }

    static Optional<FinancialFact> toFact(EventEnvelope env) {
        Instant at = env.hasOccurredAt()
                ? Instant.ofEpochSecond(env.getOccurredAt().getSeconds(), env.getOccurredAt().getNanos())
                : null;
        Optional<FinancialFact> captured = EventEnvelopes.unpack(env, PaymentCaptured.class)
                .map(c -> new FinancialFact.PaymentCaptured(env.getId(), at, c.getPaymentId(), c.getOrderId(),
                        c.getProvider(), amount(c.getAmount()), c.getAmount().getCurrencyCode()));
        if (captured.isPresent()) {
            return captured;
        }
        return EventEnvelopes.unpack(env, PaymentRefunded.class)
                .map(r -> new FinancialFact.PaymentRefunded(env.getId(), at, r.getPaymentId(), r.getOrderId(),
                        r.getProvider(), r.getRefundId().isEmpty() ? "full-" + r.getOrderId() : r.getRefundId(),
                        amount(r.getAmount()), r.getAmount().getCurrencyCode()));
    }

    static BigDecimal amount(Money m) {
        return BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNanos(), 9));
    }
}
