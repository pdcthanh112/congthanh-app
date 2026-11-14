package com.congthanh.subscriptionservice.adapter.in.messaging;

import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.order.v1.OrderCancelled;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import com.congthanh.subscriptionservice.application.port.in.SubscriptionUseCases;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Đơn gia hạn bị huỷ trong saga (thẻ từ chối khi charge off-session, hết hàng) đưa gói vào dunning. */
@Component
@RequiredArgsConstructor
class OrderEventsConsumer {

    static final String CONSUMER = "subscription-service";

    private final SubscriptionUseCases subs;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    @KafkaListener(topics = "ecommerce.order.events", groupId = CONSUMER)
    void onOrderEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        EventEnvelopes.unpack(env, OrderCancelled.class).ifPresent(e -> tx.executeWithoutResult(s -> {
            if (processed.markProcessed(env.getId(), CONSUMER)) {
                subs.renewalOrderCancelled(e.getOrderId(), e.getReason());
            }
        }));
    }
}
