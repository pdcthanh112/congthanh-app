package com.congthanh.orderservice.config;

import com.congthanh.ecom.workflow.TaskQueues;
import com.congthanh.orderservice.adapter.in.workflow.CheckoutActivitiesImpl;
import com.congthanh.orderservice.adapter.in.workflow.CheckoutWorkflowImpl;
import com.congthanh.platform.temporal.TemporalWorkerRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** order-service poll task queue "checkout": chạy workflow Checkout và activity Checkout.*. */
@Configuration
class TemporalWorkerConfig {

    @Bean
    TemporalWorkerRegistration checkoutWorker(CheckoutActivitiesImpl activities) {
        return new TemporalWorkerRegistration(TaskQueues.CHECKOUT, worker -> {
            worker.registerWorkflowImplementationTypes(CheckoutWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
        });
    }
}
