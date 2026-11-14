package com.congthanh.returnsservice.adapter.in.messaging;

import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.order.v1.OrderCancelled;
import com.congthanh.ecommerce.order.v1.OrderConfirmed;
import com.congthanh.ecommerce.order.v1.OrderPlaced;
import com.congthanh.ecommerce.shipping.v1.ShipmentStatusChanged;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import com.congthanh.returnsservice.application.port.in.OrderProjection;
import com.congthanh.returnsservice.domain.model.OrderSnapshot;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Component
@RequiredArgsConstructor
class OrderAndShippingConsumer {

    static final String CONSUMER = "returns-service";

    private final OrderProjection projection;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    @KafkaListener(topics = "ecommerce.order.events", groupId = CONSUMER)
    void onOrderEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        EventEnvelopes.unpack(env, OrderPlaced.class).ifPresent(e -> once(env, () -> projection.placed(snapshot(e))));
        EventEnvelopes.unpack(env, OrderConfirmed.class)
                .ifPresent(e -> once(env, () -> projection.confirmed(e.getOrderId(), blankToNull(e.getPaymentId()))));
        EventEnvelopes.unpack(env, OrderCancelled.class).ifPresent(e -> once(env, () -> projection.cancelled(e.getOrderId())));
    }

    @KafkaListener(topics = "ecommerce.shipping.events", groupId = CONSUMER)
    void onShippingEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        EventEnvelopes.unpack(env, ShipmentStatusChanged.class)
                .filter(e -> "DELIVERED".equals(e.getToStatus().toUpperCase(Locale.ROOT)))
                .ifPresent(e -> once(env, () -> projection.delivered(e.getOrderId(), occurredAt(env))));
    }

    private void once(EventEnvelope env, Runnable work) {
        tx.executeWithoutResult(s -> {
            if (processed.markProcessed(env.getId(), CONSUMER)) {
                work.run();
            }
        });
    }

    static OrderSnapshot snapshot(OrderPlaced e) {
        String currency = e.hasTotal() ? e.getTotal().getCurrencyCode() : "USD";
        return new OrderSnapshot(e.getOrderId(), e.getCustomerId(), "PLACED", null, currency,
                e.hasTotal() ? amount(e.getTotal()) : BigDecimal.ZERO,
                e.getLinesList().stream().map(l -> new OrderSnapshot.Line(l.getProductId(), l.getQuantity(),
                        amount(l.getUnitPrice()))).toList(), null);
    }

    /** Thời điểm hãng báo trạng thái (occurred_at của envelope), không phải lúc consumer đọc. */
    static Instant occurredAt(EventEnvelope env) {
        return env.hasOccurredAt()
                ? Instant.ofEpochSecond(env.getOccurredAt().getSeconds(), env.getOccurredAt().getNanos())
                : Instant.now();
    }

    static BigDecimal amount(Money m) {
        return BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNanos(), 9)).stripTrailingZeros();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
