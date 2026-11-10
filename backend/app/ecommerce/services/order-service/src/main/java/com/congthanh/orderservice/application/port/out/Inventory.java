package com.congthanh.orderservice.application.port.out;

import com.congthanh.orderservice.domain.model.OrderLine;

import java.util.List;

/** Giữ/trả/chốt tồn kho (inventory-service, Go). Mọi lệnh idempotent theo reservationId. */
public interface Inventory {

    Reservation reserve(String reservationId, String customerId, List<OrderLine> lines);

    void release(String reservationId, String reason);

    void commit(String reservationId, String orderId, List<OrderLine> lines);

    record Reservation(boolean success, String reservationId, String message) {
    }
}
