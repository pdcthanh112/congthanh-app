package com.congthanh.checkoutservice.adapter.in.messaging;

import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.FlashSaleClaim;
import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.inventoryservice.grpc.FlashSaleClaimed;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Component
class FlashSaleEventsConsumer {

    private final CheckoutUseCase checkout;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    FlashSaleEventsConsumer(CheckoutUseCase checkout, ProcessedEvents processed, TransactionTemplate tx) {
        this.checkout = checkout;
        this.processed = processed;
        this.tx = tx;
    }

    @KafkaListener(topics = "ecommerce.inventory.events", groupId = FraudEventsConsumer.CONSUMER,
            concurrency = "${checkout.flash-sale.consumer-concurrency:3}")
    void onInventoryEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        EventEnvelopes.unpack(env, FlashSaleClaimed.class).ifPresent(c -> {
            checkout.onFlashSaleClaimed(new FlashSaleClaim(c.getClaimId(), c.getSaleId(), c.getProductId(),
                    c.getCustomerId(), c.getQuantity(), Instant.ofEpochSecond(c.getExpiresAt())));
            tx.executeWithoutResult(s -> processed.markProcessed(env.getId(), FraudEventsConsumer.CONSUMER));
        });
    }
}
