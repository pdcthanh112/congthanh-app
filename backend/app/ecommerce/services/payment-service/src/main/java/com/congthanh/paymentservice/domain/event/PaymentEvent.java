package com.congthanh.paymentservice.domain.event;

import com.congthanh.paymentservice.domain.model.Money;

public sealed interface PaymentEvent {

    String orderId();

    record PaymentAuthorized(String paymentId, String orderId, Money amount, String provider) implements PaymentEvent {
    }

    record PaymentFailed(String orderId, String reason) implements PaymentEvent {
    }

    record PaymentRefunded(String paymentId, String orderId, Money amount) implements PaymentEvent {
    }
}
