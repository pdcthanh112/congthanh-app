package com.congthanh.checkoutservice.config;

import com.congthanh.checkoutservice.adapter.in.workflow.CheckoutWorkflowImpl;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase;
import com.congthanh.checkoutservice.application.port.out.Catalog;
import com.congthanh.checkoutservice.application.port.out.CheckoutWorkflows;
import com.congthanh.checkoutservice.application.port.out.FraudCheck;
import com.congthanh.checkoutservice.application.port.out.Ids;
import com.congthanh.checkoutservice.application.port.out.Orders;
import com.congthanh.checkoutservice.application.port.out.Pricing;
import com.congthanh.checkoutservice.application.port.out.Promotions;
import com.congthanh.checkoutservice.application.port.out.SessionRepository;
import com.congthanh.checkoutservice.application.port.out.Shipping;
import com.congthanh.checkoutservice.application.port.out.Tax;
import com.congthanh.checkoutservice.application.service.CheckoutService;
import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.platform.temporal.TemporalWorkerRegistration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
class CheckoutConfig {

    @Bean
    CheckoutUseCase checkoutUseCase(SessionRepository sessions, Catalog catalog, Pricing pricing, Promotions promotions,
                                    Shipping shipping, Tax tax, FraudCheck fraud, Orders orders,
                                    CheckoutWorkflows workflows, Ids ids, Clock clock,
                                    @Value("${checkout.currency:USD}") String currency,
                                    @Value("${checkout.session-ttl:PT30M}") Duration ttl) {
        return new CheckoutService(sessions, catalog, pricing, promotions, shipping, tax, fraud, orders, workflows, ids,
                clock, new CheckoutService.Settings(currency, ttl));
    }

    /** checkout-service poll queue "checkout-workflow": chỉ chạy workflow; activity ở order-service và payment-service. */
    @Bean
    TemporalWorkerRegistration checkoutWorkflowWorker() {
        return new TemporalWorkerRegistration(TaskQueues.CHECKOUT_WORKFLOW,
                worker -> worker.registerWorkflowImplementationTypes(CheckoutWorkflowImpl.class));
    }
}
