package com.congthanh.paymentservice.application.port.out;

import com.congthanh.paymentservice.domain.model.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByProviderRef(String provider, String providerRef);

    /** Lưu và ghi event vào outbox cùng transaction. Ném ConflictOnCreate nếu đơn đã có payment. */
    void save(Payment payment);

    /** Lần hoàn đã ghi nhận theo refundId (idempotency của hoàn một phần). */
    Optional<RecordedRefund> findRefund(String refundId);

    /** Lưu payment + bản ghi lần hoàn + event trong một transaction. Trùng refundId ném DataIntegrityViolation. */
    void saveRefund(Payment payment, RecordedRefund refund);

    record RecordedRefund(String refundId, String paymentId, String orderId, java.math.BigDecimal amount,
                          String currency, String reason, java.time.Instant createdAt) {
    }
}
