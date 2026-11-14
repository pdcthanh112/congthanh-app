package com.congthanh.disputeservice.application.port.in;

import com.congthanh.disputeservice.domain.model.Dispute;

import java.math.BigDecimal;
import java.time.Instant;

/** Tranh chấp do cổng báo (webhook) hoặc nhân viên nhập tay (PayPal, sandbox). Idempotent theo mã của cổng. */
public interface DisputeIntake {

    Dispute receive(ProviderDispute d);

    /**
     * @param providerPaymentRef mã giao dịch phía cổng (Stripe PaymentIntent); dùng để tìm đơn
     * @param providerStatus     needs_response, under_review, won, lost, warning_closed...
     */
    record ProviderDispute(String provider, String providerDisputeId, String providerPaymentRef, BigDecimal amount,
                           BigDecimal fee, String currency, String reasonCode, Instant evidenceDueBy,
                           String providerStatus) {
    }
}
