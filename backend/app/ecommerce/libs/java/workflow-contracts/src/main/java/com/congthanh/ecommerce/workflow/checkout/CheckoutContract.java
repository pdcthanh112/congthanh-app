package com.congthanh.ecommerce.workflow.checkout;

public final class CheckoutContract {

    public static final String WORKFLOW_TYPE = "Checkout";

    public static final String SIGNAL_PAYMENT_COMPLETED = "paymentCompleted";
    
    public static final String SIGNAL_CANCEL = "cancel";

    public static final String QUERY_STATUS = "status";

    private CheckoutContract() {
    }

    public static String workflowId(String orderId) {
        return "checkout-" + orderId;
    }

    public static String reservationId(String orderId) {
        return workflowId(orderId);
    }
}
