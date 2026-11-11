package com.congthanh.ecommerce.workflow.checkout;

public record PaymentCompleted(String paymentId, boolean success, String reason) {
}
