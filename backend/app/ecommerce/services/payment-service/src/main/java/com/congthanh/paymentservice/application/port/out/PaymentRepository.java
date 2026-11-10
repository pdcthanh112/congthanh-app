package com.congthanh.paymentservice.application.port.out;

import com.congthanh.paymentservice.domain.model.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByProviderRef(String provider, String providerRef);

    /** Lưu và ghi event vào outbox cùng transaction. Ném ConflictOnCreate nếu đơn đã có payment. */
    void save(Payment payment);
}
