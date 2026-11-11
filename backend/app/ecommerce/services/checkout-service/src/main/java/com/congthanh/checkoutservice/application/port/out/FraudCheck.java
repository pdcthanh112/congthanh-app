package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.FraudVerdict;

/** Chấm điểm rủi ro (fraud-service). Adapter quyết định fail-open khi fraud-service không trả lời kịp. */
public interface FraudCheck {

    FraudVerdict assess(CheckoutSession session, Context ctx);

    record Context(String email, String phone, String ip, String deviceId, String paymentMethod,
                   java.time.Instant accountCreatedAt) {
    }
}
