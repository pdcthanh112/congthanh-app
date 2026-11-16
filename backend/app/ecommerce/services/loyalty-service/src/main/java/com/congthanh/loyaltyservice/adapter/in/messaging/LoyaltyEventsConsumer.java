package com.congthanh.loyaltyservice.adapter.in.messaging;

import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.order.v1.OrderCancelled;
import com.congthanh.ecommerce.order.v1.OrderConfirmed;
import com.congthanh.ecommerce.returns.v1.RefundIssued;
import com.congthanh.ecommerce.shipping.v1.ShipmentStatusChanged;
import com.congthanh.loyaltyservice.application.port.in.LoyaltyUseCases;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;

/** Tích điểm khi đơn xác nhận, hẹn ngày xác nhận khi giao, đảo khi hoàn tiền/huỷ. Chỉ tính đơn dùng đơn vị tiền của chương trình điểm (cấu hình loyalty.currency). */
@Component
@RequiredArgsConstructor
class LoyaltyEventsConsumer {

    static final String CONSUMER = "loyalty-service";

    private final LoyaltyUseCases loyalty;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    @KafkaListener(topics = {"ecommerce.order.events", "ecommerce.shipping.events", "ecommerce.returns.events"},
            groupId = CONSUMER)
    void onEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        Instant at = env.hasOccurredAt()
                ? Instant.ofEpochSecond(env.getOccurredAt().getSeconds(), env.getOccurredAt().getNanos())
                : Instant.now();
        EventEnvelopes.unpack(env, OrderConfirmed.class)
                .filter(OrderConfirmed::hasTotal)
                .ifPresent(e -> once(env, () -> loyalty.orderConfirmed(e.getOrderId(), e.getCustomerId(),
                        amount(e.getTotal()), e.getTotal().getCurrencyCode())));
        EventEnvelopes.unpack(env, OrderCancelled.class).ifPresent(e -> once(env, () -> loyalty.orderCancelled(e.getOrderId())));
        EventEnvelopes.unpack(env, ShipmentStatusChanged.class).filter(e -> "DELIVERED".equalsIgnoreCase(e.getToStatus()))
                .ifPresent(e -> once(env, () -> loyalty.orderDelivered(e.getOrderId(), at)));
        EventEnvelopes.unpack(env, RefundIssued.class)
                .ifPresent(e -> once(env, () -> loyalty.refunded(e.getOrderId(), amount(e.getAmount()),
                        e.getAmount().getCurrencyCode(), "RETURN " + e.getReturnId())));
    }

    // Use case tự mở transaction; processed_events dùng transaction riêng bọc ngoài (lồng nhau cùng transaction).
    private void once(EventEnvelope env, Runnable work) {
        tx.executeWithoutResult(s -> {
            if (processed.markProcessed(env.getId(), CONSUMER)) {
                work.run();
            }
        });
    }

    static BigDecimal amount(Money m) {
        return BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNanos(), 9)).stripTrailingZeros();
    }
}
