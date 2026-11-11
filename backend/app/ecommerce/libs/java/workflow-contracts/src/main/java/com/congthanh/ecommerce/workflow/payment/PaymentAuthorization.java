package com.congthanh.ecommerce.workflow.payment;

/**
 * @param status      AUTHORIZED: đã giữ tiền; ACTION_REQUIRED: khách phải sang cổng thanh toán
 *                    ({@code redirectUrl}), kết quả về sau qua signal paymentCompleted
 */
public record PaymentAuthorization(String paymentId, Status status, String redirectUrl) {

    public enum Status { AUTHORIZED, ACTION_REQUIRED }
}
