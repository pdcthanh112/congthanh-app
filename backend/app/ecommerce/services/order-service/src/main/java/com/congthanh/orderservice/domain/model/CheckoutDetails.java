package com.congthanh.orderservice.domain.model;

/**
 * Thông tin thanh toán đi kèm đơn, lưu lại để có thể khởi động lại saga nếu lần start đầu thất bại.
 *
 * @param paymentToken token của cổng thanh toán phía client (ví dụ Stripe PaymentMethod id), không phải số thẻ
 */
public record CheckoutDetails(PaymentMethod paymentMethod, String paymentToken, String returnUrl, String clientIp) {

    public CheckoutDetails {
        if (paymentMethod == null) {
            throw com.congthanh.orderservice.domain.exception.OrderException.invalid("paymentMethod is required");
        }
    }
}
