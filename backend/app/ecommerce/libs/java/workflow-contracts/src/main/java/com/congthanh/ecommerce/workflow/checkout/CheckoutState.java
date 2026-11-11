package com.congthanh.ecommerce.workflow.checkout;

public record CheckoutState(Step step, String paymentRedirectUrl, String failureReason) {

    public enum Step {
        STARTED, REDEEMING_PROMOTION, RESERVING_INVENTORY, AUTHORIZING_PAYMENT, AWAITING_PAYMENT,
        COMMITTING_INVENTORY, CAPTURING_PAYMENT, COMPLETED, COMPENSATING, CANCELLED
    }
}
