package com.congthanh.ecom.workflow.payment;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity thanh toán, chạy trên worker của payment-service (task queue {@code payment}).
 * Mọi method đều idempotent theo {@code orderId}: Temporal có thể gọi lại sau timeout/retry,
 * và lần gọi lại phải trả đúng kết quả cũ chứ không trừ tiền lần hai.
 */
@ActivityInterface(namePrefix = "Payment.")
public interface PaymentActivities {

    /** Giữ tiền (authorize). Thẻ bị từ chối ném ApplicationFailure không retry, type {@link PaymentFailures#DECLINED}. */
    @ActivityMethod
    PaymentAuthorization authorize(AuthorizePaymentRequest request);

    /** Thu tiền đã giữ, sau khi kho đã commit. COD: đánh dấu thu khi giao hàng. */
    @ActivityMethod
    void capture(String orderId);

    /** Bù trừ: huỷ giữ tiền nếu chưa capture, hoàn tiền nếu đã capture, không làm gì nếu chưa có payment. */
    @ActivityMethod
    void refund(RefundPaymentRequest request);
}
