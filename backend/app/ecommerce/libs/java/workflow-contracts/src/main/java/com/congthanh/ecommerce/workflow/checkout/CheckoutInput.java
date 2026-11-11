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
        long paymentTimeoutSeconds) {
}
