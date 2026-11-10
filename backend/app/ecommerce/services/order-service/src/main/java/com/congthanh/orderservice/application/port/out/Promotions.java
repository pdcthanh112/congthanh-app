package com.congthanh.orderservice.application.port.out;

import com.congthanh.orderservice.domain.model.Money;

/** Voucher (promotion-service). */
public interface Promotions {

    DiscountQuote quote(String code, String customerId, Money subtotal);

    /** Giữ một lượt dùng cho đơn; idempotent theo orderId. */
    DiscountQuote redeem(String code, String customerId, String orderId, Money subtotal);

    void cancelRedemption(String orderId, String reason);

    record DiscountQuote(boolean valid, Money discount, String reason) {
    }
}
