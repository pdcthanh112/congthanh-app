package com.congthanh.ecom.workflow.checkout;

/** Payload của signal {@link CheckoutContract#SIGNAL_PAYMENT_COMPLETED}. */
public record PaymentCompleted(String paymentId, boolean success, String reason) {
}
