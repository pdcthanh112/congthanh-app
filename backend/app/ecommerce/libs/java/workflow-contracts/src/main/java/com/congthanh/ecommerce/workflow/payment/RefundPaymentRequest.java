package com.congthanh.ecommerce.workflow.payment;

public record RefundPaymentRequest(String orderId, String reason) {
}
