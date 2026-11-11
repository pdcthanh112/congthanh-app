package com.congthanh.orderservice.domain.model;

/** Cùng bộ giá trị với payment-service. AFFIRM, KLARNA, AFTERPAY: trả góp qua đối tác (ADR 0016). */
public enum PaymentMethod {
    CARD, APPLE_PAY, GOOGLE_PAY, PAYPAL, AFFIRM, KLARNA, AFTERPAY
}
