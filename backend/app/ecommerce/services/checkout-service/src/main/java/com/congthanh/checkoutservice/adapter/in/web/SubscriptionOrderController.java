package com.congthanh.checkoutservice.adapter.in.web;

import com.congthanh.checkoutservice.application.port.in.SubscriptionOrders;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Chỉ gọi nội bộ từ subscription-service: không có route ở APISIX, Istio AuthorizationPolicy chỉ mở cho
 * service account subscription-service.
 */
@RestController
@RequestMapping("/internal/checkout")
class SubscriptionOrderController {

    private final SubscriptionOrders orders;

    SubscriptionOrderController(SubscriptionOrders orders) {
        this.orders = orders;
    }

    @PostMapping("/subscription-orders")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionOrders.Result place(@Valid @RequestBody Request r) {
        return orders.place(new SubscriptionOrders.Command(r.idempotencyKey(), r.subscriptionId(), r.customerId(),
                r.lines().stream().map(l -> new CheckoutSession.Item(l.productId(), l.quantity())).toList(),
                r.addressId(), r.paymentMethodRef(), r.currency(), r.promotionCode()));
    }

    record Line(@NotBlank String productId, @Positive int quantity) {
    }

    record Request(@NotBlank String idempotencyKey, @NotBlank String subscriptionId, @NotBlank String customerId,
                   @NotEmpty List<@Valid Line> lines, String addressId, @NotBlank String paymentMethodRef,
                   @NotBlank String currency, String promotionCode) {
    }
}
