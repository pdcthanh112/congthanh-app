package com.congthanh.ecommerce.workflow;

public final class TaskQueues {

    public static final String CHECKOUT_WORKFLOW = "checkout-workflow";
    public static final String CHECKOUT = "checkout";
    public static final String PAYMENT = "payment";
    /** Workflow hoàn tiền trả hàng và activity của returns-service. */
    public static final String RETURNS = "returns";
    /** Activity store credit / gift card của stored-value-service. */
    public static final String STORED_VALUE = "stored-value";
    /** Activity đổi điểm của loyalty-service. */
    public static final String LOYALTY = "loyalty";

    private TaskQueues() {
    }
}
