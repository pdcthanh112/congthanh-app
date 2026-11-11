package com.congthanh.orderservice.config;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.orderservice.adapter.in.workflow.CheckoutActivitiesImpl;
import com.congthanh.platform.temporal.TemporalWorkerRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * order-service poll task queue "checkout": chỉ chạy activity Checkout.* (đơn, kho, voucher).
 * Workflow Checkout chạy ở checkout-service (queue "checkout-workflow").
 */
@Configuration
class TemporalWorkerConfig {

    @Bean
    TemporalWorkerRegistration checkoutActivitiesWorker(CheckoutActivitiesImpl activities) {
        return new TemporalWorkerRegistration(TaskQueues.CHECKOUT, worker -> worker.registerActivitiesImplementations(activities));
    }
}
