package com.congthanh.returnsservice.application.port.in;

import com.congthanh.returnsservice.domain.model.OrderSnapshot;

import java.time.Instant;

/** Dựng bản chiếu đơn hàng từ event của order/shipping. */
public interface OrderProjection {

    void placed(OrderSnapshot order);

    void confirmed(String orderId, String paymentId);

    void cancelled(String orderId);

    void delivered(String orderId, Instant at);
}
