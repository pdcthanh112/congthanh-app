package com.congthanh.ecommerce.workflow.checkout;

import java.math.BigDecimal;

public record CheckoutInput(
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String paymentToken,
        String returnUrl,
        String clientIp,
        boolean hasPromotion,
        long paymentTimeoutSeconds,
        // ADR 0027: amount là TỔNG đơn; workflow trừ store credit + giá trị điểm rồi mới charge cổng phần còn lại.
        // Workflow cũ (payload không có hai trường này) đọc ra null/0 nên chạy như trước.
        BigDecimal storeCredit,
        long loyaltyPoints) {

    public CheckoutInput(String orderId, String customerId, BigDecimal amount, String currency, String paymentMethod,
                         String paymentToken, String returnUrl, String clientIp, boolean hasPromotion,
                         long paymentTimeoutSeconds) {
        this(orderId, customerId, amount, currency, paymentMethod, paymentToken, returnUrl, clientIp, hasPromotion,
                paymentTimeoutSeconds, null, 0);
    }

    public boolean usesStoreCredit() {
        return storeCredit != null && storeCredit.signum() > 0;
    }

    public boolean usesLoyaltyPoints() {
        return loyaltyPoints > 0;
    }
}
