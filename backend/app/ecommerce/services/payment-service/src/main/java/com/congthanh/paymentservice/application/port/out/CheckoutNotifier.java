package com.congthanh.paymentservice.application.port.out;

/** Báo cho saga checkout (order-service) khi khách trả xong ở cổng redirect. */
public interface CheckoutNotifier {

    void paymentCompleted(String orderId, String paymentId, boolean success, String reason);
}
