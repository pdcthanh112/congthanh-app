package com.congthanh.orderservice.application.port.out;

/** Sinh id nội bộ (UUID) và mã đơn cho khách (ngắn, dễ đọc). */
public interface OrderIdentity {

    String newOrderId();

    String newOrderCode();
}
