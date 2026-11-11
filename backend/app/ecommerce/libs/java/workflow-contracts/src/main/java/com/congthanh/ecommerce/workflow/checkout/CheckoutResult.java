package com.congthanh.ecommerce.workflow.checkout;

public record CheckoutResult(String orderId, boolean confirmed, String paymentId, String failureReason) {
}
