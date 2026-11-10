package com.congthanh.orderservice.adapter.in.workflow;

import java.math.BigDecimal;

/**
 * Input của workflow: đủ để gọi payment-service mà không cần đọc DB của order-service.
 * Đổi record này phải tương thích ngược (thêm field mới ở cuối, có giá trị mặc định) vì
 * workflow đang chạy sẽ được replay với input cũ.
 */
public record CheckoutInput(
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String paymentToken,
        String returnUrl,
        String clientIp,
        boolean hasPromotion,
        long paymentTimeoutSeconds) {
}
