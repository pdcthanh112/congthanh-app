package com.congthanh.paymentservice.application.port.out;

import com.congthanh.paymentservice.domain.model.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByProviderRef(String provider, String providerRef);

    void save(Payment payment);

    Optional<RecordedRefund> findRefund(String refundId);

    void saveRefund(Payment payment, RecordedRefund refund);

    record RecordedRefund(String refundId, String paymentId, String orderId, java.math.BigDecimal amount,
                          String currency, String reason, java.time.Instant createdAt) {
    }
}
