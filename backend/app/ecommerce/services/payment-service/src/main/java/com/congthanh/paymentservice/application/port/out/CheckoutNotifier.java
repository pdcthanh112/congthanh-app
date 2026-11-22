package com.congthanh.paymentservice.application.port.out;

public interface CheckoutNotifier {

    void paymentCompleted(String orderId, String paymentId, boolean success, String reason);
}
