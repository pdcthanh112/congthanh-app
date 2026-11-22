package com.congthanh.checkoutservice.application.port.in;

import com.congthanh.checkoutservice.domain.model.CheckoutSession;

import java.math.BigDecimal;
import java.util.List;

public interface SubscriptionOrders {

    Result place(Command command);

    record Command(String idempotencyKey, String subscriptionId, String customerId, List<CheckoutSession.Item> items,
                   String addressId, String paymentMethodRef, String currency, String promotionCode) {
    }

    record Result(String orderId, String orderCode, BigDecimal total, String currency) {
    }
}
