package com.congthanh.disputeservice.adapter.in.messaging;

import com.congthanh.disputeservice.application.port.in.FactsProjection;
import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.order.v1.OrderLine;
import com.congthanh.ecommerce.order.v1.OrderPlaced;
import com.congthanh.ecommerce.payment.v1.PaymentCaptured;
import com.congthanh.ecommerce.payment.v1.PaymentRefunded;
import com.congthanh.ecommerce.returns.v1.RefundIssued;
import com.congthanh.ecommerce.shipping.v1.ShipmentStatusChanged;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dữ kiện cho bằng chứng: đơn (OrderPlaced), mã giao dịch phía cổng (PaymentCaptured), vận đơn/ngày giao
 * (ShipmentStatusChanged), tiền đã hoàn (PaymentRefunded; RefundIssued chỉ khi hoàn bằng store credit, vì hoàn về
 * phương thức gốc đã có PaymentRefunded).
 */
@Component
@RequiredArgsConstructor
class FactsConsumer {

    static final String CONSUMER = "dispute-service";

    private final FactsProjection projection;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    @KafkaListener(topics = {"ecommerce.order.events", "ecommerce.payment.events", "ecommerce.shipping.events",
            "ecommerce.returns.events"}, groupId = CONSUMER)
    void onEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        Instant at = env.hasOccurredAt()
                ? Instant.ofEpochSecond(env.getOccurredAt().getSeconds(), env.getOccurredAt().getNanos())
                : Instant.now();
        EventEnvelopes.unpack(env, OrderPlaced.class).ifPresent(e -> once(env, () -> projection.orderPlaced(
                e.getOrderId(), e.getCustomerId(), blank(e.getShippingAddress().getRecipientName()),
                blank(e.getShippingAddress().getRegionCode()), blank(e.getShippingAddress().getPostalCode()),
                e.getLinesList().stream().map(OrderLine::getProductId).toList(), amount(e.getTotal()))));
        EventEnvelopes.unpack(env, PaymentCaptured.class).ifPresent(e -> once(env, () -> projection.paymentCaptured(
                e.getPaymentId(), e.getOrderId(), blank(e.getCustomerId()), e.getProvider(), e.getProviderRef())));
        EventEnvelopes.unpack(env, PaymentRefunded.class).ifPresent(e -> once(env, () -> projection.refunded(
                e.getOrderId(), "payment:" + (e.getRefundId().isEmpty() ? env.getId() : e.getRefundId()),
                amount(e.getAmount()), at)));
        EventEnvelopes.unpack(env, RefundIssued.class).filter(e -> "STORE_CREDIT".equals(e.getMethod()))
                .ifPresent(e -> once(env, () -> projection.refunded(e.getOrderId(), "credit:" + e.getReturnId(),
                        amount(e.getAmount()), at)));
        EventEnvelopes.unpack(env, ShipmentStatusChanged.class).ifPresent(e -> once(env, () -> projection.shipmentStatus(
                e.getOrderId(), e.getToStatus(), blank(e.getCarrier()), blank(e.getTrackingNumber()), at)));
    }

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

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
