package com.congthanh.orderservice.domain.event;

import com.congthanh.orderservice.domain.model.Address;
import com.congthanh.orderservice.domain.model.Money;
import com.congthanh.orderservice.domain.model.OrderLine;

import java.util.List;

/** Event miền của Order; adapter persistence chuyển thành protobuf và ghi outbox cùng transaction. */
public sealed interface OrderEvent {

    String orderId();

    record OrderPlaced(String orderId, String customerId, List<OrderLine> lines, Money total,
                       String checkoutWorkflowId, Address shippingAddress) implements OrderEvent {
    }

    record OrderConfirmed(String orderId, String paymentId) implements OrderEvent {
    }

    record OrderCancelled(String orderId, String reason, String reservationId) implements OrderEvent {
    }
}
