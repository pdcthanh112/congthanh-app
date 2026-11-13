package com.congthanh.promotionservice.adapter.in.messaging;

import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.order.v1.OrderCancelled;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import com.congthanh.promotionservice.application.port.in.CheckoutDiscounts;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
class OrderEventsConsumer {

    static final String CONSUMER = "promotion-service";

    private final CheckoutDiscounts discounts;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    @KafkaListener(topics = "ecommerce.order.events", groupId = CONSUMER)
    void onOrderEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        EventEnvelopes.unpack(env, OrderCancelled.class).ifPresent(cancelled -> tx.executeWithoutResult(s -> {
            if (processed.markProcessed(env.getId(), CONSUMER)) {
                discounts.cancelRedemption(cancelled.getOrderId(), "order cancelled: " + cancelled.getReason());
            }
        }));
    }
}
