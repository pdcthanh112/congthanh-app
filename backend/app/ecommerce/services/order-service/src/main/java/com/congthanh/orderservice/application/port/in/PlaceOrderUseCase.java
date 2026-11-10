package com.congthanh.orderservice.application.port.in;

import com.congthanh.orderservice.domain.model.Address;
import com.congthanh.orderservice.domain.model.Money;
import com.congthanh.orderservice.domain.model.OrderStatus;
import com.congthanh.orderservice.domain.model.PaymentMethod;

import java.util.List;

/** Tạo đơn từ giỏ hàng và khởi động saga checkout. */
public interface PlaceOrderUseCase {

    PlacedOrder place(PlaceOrderCommand command);

    record Item(String productId, int quantity) {
    }

    record PlaceOrderCommand(String customerId, List<Item> items, String promotionCode,
                             PaymentMethod paymentMethod, String paymentToken, String returnUrl, String clientIp,
                             Address shippingAddress, Address billingAddress, String note) {
    }

    record PlacedOrder(String orderId, String orderCode, OrderStatus status, Money total, Money discount) {
    }
}
