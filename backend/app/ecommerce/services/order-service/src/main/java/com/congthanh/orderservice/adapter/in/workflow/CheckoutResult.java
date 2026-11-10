package com.congthanh.orderservice.adapter.in.workflow;

public record CheckoutResult(String orderId, boolean confirmed, String paymentId, String failureReason) {
}
