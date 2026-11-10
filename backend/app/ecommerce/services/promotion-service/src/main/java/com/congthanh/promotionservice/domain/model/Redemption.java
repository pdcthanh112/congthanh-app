package com.congthanh.promotionservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Một lượt dùng voucher của một đơn (order_id unique → redeem idempotent). */
public record Redemption(Long promotionId, String orderId, String customerId, BigDecimal discount, String currency,
                         boolean cancelled, Instant redeemedAt) {

    public Redemption cancel() {
        return new Redemption(promotionId, orderId, customerId, discount, currency, true, redeemedAt);
    }
}
