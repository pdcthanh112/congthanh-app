package com.congthanh.orderservice.application.port.out;

import com.congthanh.orderservice.domain.model.Order;

import java.util.Optional;

/** Saga checkout chạy bền vững (Temporal). Start idempotent theo order id. */
public interface CheckoutOrchestrator {

    void start(Order order);

    Optional<Progress> progress(String orderId);

    void requestCancel(String orderId, String reason);

    record Progress(String step, String paymentRedirectUrl, String failureReason) {
    }
}
