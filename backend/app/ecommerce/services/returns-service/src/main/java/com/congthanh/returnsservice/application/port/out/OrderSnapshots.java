package com.congthanh.returnsservice.application.port.out;

import com.congthanh.returnsservice.domain.model.OrderSnapshot;

import java.time.Instant;
import java.util.Optional;

public interface OrderSnapshots {

    Optional<OrderSnapshot> find(String orderId);

    /** Khoá hàng của đơn tới hết transaction, để hai yêu cầu trả song song không vượt số lượng đã mua. */
    Optional<OrderSnapshot> lock(String orderId);

    void upsertPlaced(OrderSnapshot order);

    void markStatus(String orderId, String status, String paymentId);

    void markDelivered(String orderId, Instant at);
}
