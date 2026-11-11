package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.CheckoutSession;

/** Tạo đơn ở order-service (gRPC nội bộ, idempotent theo order id của phiên); order-service start saga. */
public interface Orders {

    Placed place(CheckoutSession session);

    record Placed(String orderId, String orderCode, String status) {
    }
}
