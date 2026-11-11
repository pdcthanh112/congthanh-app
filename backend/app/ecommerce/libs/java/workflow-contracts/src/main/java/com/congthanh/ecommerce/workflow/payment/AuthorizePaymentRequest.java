package com.congthanh.ecommerce.workflow.payment;

import java.math.BigDecimal;

/**
 * @param method       CARD, APPLE_PAY, GOOGLE_PAY, PAYPAL, AFFIRM, KLARNA, AFTERPAY (trả góp, ADR 0016)
 * @param paymentToken token phía client (ví dụ Stripe PaymentMethod id); không bao giờ là số thẻ
 * @param returnUrl    URL frontend để cổng redirect về sau khi khách thanh toán; bắt buộc với PayPal và trả góp
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
