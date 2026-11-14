package com.congthanh.disputeservice.application.port.out;

import com.congthanh.disputeservice.domain.model.OrderFacts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FactsStore {

    Optional<OrderFacts> orderFacts(String orderId);

    Optional<PaymentRef> paymentByRef(String provider, String providerRef);

    void upsertOrder(String orderId, String customerId, String recipientName, String region, String postalCode,
                     List<String> productIds, BigDecimal total);

    void upsertPayment(PaymentRef ref);

    void upsertShipment(String orderId, String status, String carrier, String trackingNumber, Instant at);

    /** Idempotent theo refundKey. */
    void addRefund(String orderId, String refundKey, BigDecimal amount, Instant at);

    record PaymentRef(String paymentId, String orderId, String customerId, String provider, String providerRef) {
    }
}
