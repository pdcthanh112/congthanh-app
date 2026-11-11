package com.congthanh.checkoutservice.application.port.out;

import java.util.Optional;

/** Saga checkout trên Temporal (workflow id = checkout-&lt;orderId&gt;). */
public interface CheckoutWorkflows {

    Optional<Progress> progress(String orderId);

    void requestCancel(String orderId, String reason);

    record Progress(String step, String paymentRedirectUrl, String failureReason) {
    }
}
