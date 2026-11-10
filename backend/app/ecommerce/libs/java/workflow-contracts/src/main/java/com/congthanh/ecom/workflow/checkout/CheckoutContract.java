package com.congthanh.ecom.workflow.checkout;

/** Những gì service khác cần biết về workflow checkout (không phụ thuộc code của order-service). */
public final class CheckoutContract {

    /** Tên workflow type đăng ký với Temporal. */
    public static final String WORKFLOW_TYPE = "Checkout";
    /** Signal payment-service gửi khi cổng thanh toán redirect (VNPay, PayPal) báo kết quả. */
    public static final String SIGNAL_PAYMENT_COMPLETED = "paymentCompleted";
    /** Signal khách huỷ đơn khi chưa thanh toán xong. */
    public static final String SIGNAL_CANCEL = "cancel";
    /** Query trạng thái hiện tại (bước đang chạy, URL thanh toán nếu có). */
    public static final String QUERY_STATUS = "status";

    private CheckoutContract() {
    }

    /** Workflow id gắn với order id: start lại cùng order là no-op, không tạo saga thứ hai. */
    public static String workflowId(String orderId) {
        return "checkout-" + orderId;
    }

    /** Reservation tồn kho dùng chung id với workflow để inventory-service xử lý idempotent. */
    public static String reservationId(String orderId) {
        return workflowId(orderId);
    }
}
