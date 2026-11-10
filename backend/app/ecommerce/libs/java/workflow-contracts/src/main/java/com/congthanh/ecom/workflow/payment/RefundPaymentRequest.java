package com.congthanh.ecom.workflow.payment;

public record RefundPaymentRequest(String orderId, String reason) {
}
