package com.congthanh.checkoutservice.adapter.in.messaging;

import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase;
import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.fraud.v1.FraudReviewDecided;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nhân viên duyệt xong giao dịch đáng ngờ → tiếp tục (tạo đơn) hoặc từ chối phiên. Use case idempotent theo trạng
 * thái phiên; processed_events chỉ đánh dấu sau khi xử lý thành công để lỗi tạm thời được Kafka giao lại.
 */
@Component
class FraudEventsConsumer {

    static final String CONSUMER = "checkout-service";

    private final CheckoutUseCase checkout;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    FraudEventsConsumer(CheckoutUseCase checkout, ProcessedEvents processed, TransactionTemplate tx) {
        this.checkout = checkout;
        this.processed = processed;
        this.tx = tx;
    }

    @KafkaListener(topics = "ecommerce.fraud.events", groupId = CONSUMER)
    void onFraudEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        EventEnvelopes.unpack(env, FraudReviewDecided.class).ifPresent(d -> {
            checkout.onFraudReviewDecided(d.getCheckoutId(), "APPROVE".equals(d.getDecision()), d.getNote());
            tx.executeWithoutResult(s -> processed.markProcessed(env.getId(), CONSUMER));
        });
    }
}
