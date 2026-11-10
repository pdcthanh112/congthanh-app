package com.congthanh.orderservice.application.port.in;

import com.congthanh.orderservice.domain.model.Order;

import java.util.List;

public interface OrderQueries {

    Order get(String orderCode, Requester requester);

    List<Order> history(Requester requester, int limit);

    CheckoutProgress progress(String orderCode, Requester requester);

    /** @param step bước saga hiện tại (STARTED, RESERVING, AWAITING_PAYMENT...) hoặc trạng thái đơn khi saga đã xong */
    record CheckoutProgress(String orderCode, String orderStatus, String step, String paymentRedirectUrl,
                            String failureReason) {
    }
}
