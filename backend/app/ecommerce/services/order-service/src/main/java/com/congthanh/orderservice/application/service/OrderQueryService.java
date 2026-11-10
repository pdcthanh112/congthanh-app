package com.congthanh.orderservice.application.service;

import com.congthanh.orderservice.application.port.in.OrderQueries;
import com.congthanh.orderservice.application.port.in.Requester;
import com.congthanh.orderservice.application.port.out.CheckoutOrchestrator;
import com.congthanh.orderservice.application.port.out.OrderRepository;
import com.congthanh.orderservice.domain.exception.OrderException;
import com.congthanh.orderservice.domain.model.Order;
import com.congthanh.orderservice.domain.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderQueryService implements OrderQueries {

    private final OrderRepository orders;
    private final CheckoutOrchestrator checkout;

    public OrderQueryService(OrderRepository orders, CheckoutOrchestrator checkout) {
        this.orders = orders;
        this.checkout = checkout;
    }

    @Override
    public Order get(String orderCode, Requester requester) {
        Order order = orders.findByCode(orderCode)
                .orElseThrow(() -> OrderException.notFound("order " + orderCode + " not found"));
        // Không lộ việc đơn tồn tại với người không có quyền.
        if (!requester.canAccess(order.customerId())) {
            throw OrderException.notFound("order " + orderCode + " not found");
        }
        return order;
    }

    @Override
    public List<Order> history(Requester requester, int limit) {
        return orders.findByCustomer(requester.subject(), Math.clamp(limit, 1, 100));
    }

    @Override
    public CheckoutProgress progress(String orderCode, Requester requester) {
        Order order = get(orderCode, requester);
        boolean finished = order.status() == OrderStatus.CONFIRMED || order.status() == OrderStatus.CANCELLED;
        if (!finished) {
            var live = checkout.progress(order.id());
            if (live.isPresent()) {
                var p = live.get();
                return new CheckoutProgress(orderCode, order.status().name(), p.step(), p.paymentRedirectUrl(), p.failureReason());
            }
        }
        return new CheckoutProgress(orderCode, order.status().name(), order.status().name(), null, order.cancelReason());
    }
}
