package com.congthanh.paymentservice.application.port.out;

import com.congthanh.paymentservice.domain.model.Money;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;

import java.util.Map;
import java.util.Set;

public interface PaymentProvider {

    String name();

    Set<PaymentMethod> methods();

    ProviderResult authorize(Payment payment, String paymentToken, String returnUrl, String clientIp);

    boolean capture(Payment payment);

    void voidAuthorization(Payment payment);

    void refund(Payment payment);

    void refundPartial(Payment payment, String refundId, Money amount);

    default CallbackResult verifyCallback(Map<String, String> params) {
        throw new UnsupportedOperationException(name() + " has no callbacks");
    }

    sealed interface ProviderResult {
        record Authorized(String providerRef) implements ProviderResult {
        }

        record ActionRequired(String providerRef, String redirectUrl) implements ProviderResult {
        }

        record Declined(String reason) implements ProviderResult {
        }
    }

    record CallbackResult(String orderId, String providerRef, boolean success, String reason, Long amountMinor) {
    }
}
