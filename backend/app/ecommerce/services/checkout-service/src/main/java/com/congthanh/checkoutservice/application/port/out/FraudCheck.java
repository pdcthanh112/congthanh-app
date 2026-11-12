package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.FraudVerdict;

public interface FraudCheck {

    FraudVerdict assess(CheckoutSession session, Context ctx);

    record Context(String email, String phone, String ip, String deviceId, String paymentMethod,
                   java.time.Instant accountCreatedAt) {
    }
}
