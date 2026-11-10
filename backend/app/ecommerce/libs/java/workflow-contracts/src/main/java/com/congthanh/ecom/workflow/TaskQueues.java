package com.congthanh.ecom.workflow;

/**
 * Task queue của Temporal. Mỗi queue do đúng một service poll:
 * order-service chạy workflow checkout + activity order/inventory/promotion,
 * payment-service chạy activity thanh toán.
 */
public final class TaskQueues {

    public static final String CHECKOUT = "checkout";
    public static final String PAYMENT = "payment";

    private TaskQueues() {
    }
}
