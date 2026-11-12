package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.CheckoutSession;

public interface Orders {

    Placed place(CheckoutSession session);

    record Placed(String orderId, String orderCode, String status) {
    }
}
