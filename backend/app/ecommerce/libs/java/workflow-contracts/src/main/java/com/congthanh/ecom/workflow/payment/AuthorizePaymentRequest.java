package com.congthanh.ecom.workflow.payment;

import java.math.BigDecimal;

/**
 * @param method       CASH_ON_DELIVERY, CREDIT_CARD, STRIPE, PAYPAL, VN_PAY...
 * @param paymentToken token phía client (ví dụ Stripe PaymentMethod id); không bao giờ là số thẻ
 * @param returnUrl    URL frontend để cổng redirect về sau khi khách thanh toán
 */
public record AuthorizePaymentRequest(
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String method,
        String paymentToken,
        String returnUrl,
        String clientIp) {
}
