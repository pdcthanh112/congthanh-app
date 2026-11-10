package com.congthanh.promotionservice.application.port.in;

import com.congthanh.promotionservice.domain.model.Quote;

import java.math.BigDecimal;

/** Dùng bởi saga checkout của order-service (qua gRPC). */
public interface CheckoutDiscounts {

    Quote quote(String code, String customerId, BigDecimal subtotal, String currency);

    /** Idempotent theo orderId: gọi lại trả đúng lượt đã giữ. */
    Quote redeem(String code, String customerId, String orderId, BigDecimal subtotal, String currency);

    /** Idempotent: đơn chưa redeem hoặc đã huỷ thì không làm gì. @return true nếu vừa huỷ một lượt */
    boolean cancelRedemption(String orderId, String reason);
}
