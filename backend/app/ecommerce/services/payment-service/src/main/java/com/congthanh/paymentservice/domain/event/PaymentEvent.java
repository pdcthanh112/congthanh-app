package com.congthanh.paymentservice.domain.event;

import com.congthanh.paymentservice.domain.model.Money;

public sealed interface PaymentEvent {

    String orderId();

    record PaymentAuthorized(String paymentId, String orderId, Money amount, String provider, String method) implements PaymentEvent {
    }

    record PaymentFailed(String orderId, String reason) implements PaymentEvent {
    }

    record PaymentCaptured(String paymentId, String orderId, String customerId, Money amount, String provider,
                           String method, String providerRef) implements PaymentEvent {
    }

    record PaymentRefunded(String paymentId, String orderId, Money amount, String refundId, String reason,
                           String provider, boolean fullyRefunded, String providerRef) implements PaymentEvent {

        public PaymentRefunded(String paymentId, String orderId, Money amount) {
            this(paymentId, orderId, amount, "full-" + orderId, null, null, true, null);
        }
    }
}
