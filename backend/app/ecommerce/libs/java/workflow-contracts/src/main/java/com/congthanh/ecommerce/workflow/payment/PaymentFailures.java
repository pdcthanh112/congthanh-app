package com.congthanh.ecommerce.workflow.payment;

/** Kiểu ApplicationFailure mà workflow xử lý như lỗi nghiệp vụ (không retry). */
public final class PaymentFailures {

    public static final String DECLINED = "PaymentDeclined";
    public static final String UNSUPPORTED_METHOD = "UnsupportedPaymentMethod";

    private PaymentFailures() {
    }
}
