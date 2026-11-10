package com.congthanh.orderservice.application.service;

import com.congthanh.orderservice.application.port.out.CheckoutOrchestrator;
import com.congthanh.orderservice.application.port.out.OrderRepository;
import com.congthanh.orderservice.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Lưới an toàn cho khoảng hở "đã commit đơn nhưng start workflow lỗi" (Temporal tạm không với tới).
 * Start lại là an toàn: workflow id cố định theo order, Temporal trả về execution đang có.
 */
@Component
public class CheckoutRecovery {

    private static final Logger log = LoggerFactory.getLogger(CheckoutRecovery.class);

    private final OrderRepository orders;
    private final CheckoutOrchestrator checkout;
    private final Clock clock;
    private final Duration grace;

    public CheckoutRecovery(OrderRepository orders, CheckoutOrchestrator checkout, Clock clock,
                            @Value("${order.checkout.recovery-grace:PT1M}") Duration grace) {
        this.orders = orders;
        this.checkout = checkout;
        this.clock = clock;
        this.grace = grace;
    }

    @Scheduled(fixedDelayString = "${order.checkout.recovery-interval:PT1M}", initialDelayString = "PT30S")
    public void restartStuckCheckouts() {
        for (Order order : orders.findPendingPlacedBefore(clock.instant().minus(grace), 100)) {
            try {
                checkout.start(order);
            } catch (RuntimeException e) {
                log.warn("recovery could not start checkout for {}: {}", order.id(), e.toString());
            }
        }
    }
}
