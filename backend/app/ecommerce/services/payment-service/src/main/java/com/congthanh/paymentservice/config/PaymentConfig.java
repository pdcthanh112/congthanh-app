package com.congthanh.paymentservice.config;

import com.congthanh.ecom.workflow.TaskQueues;
import com.congthanh.paymentservice.adapter.in.workflow.PaymentActivitiesImpl;
import com.congthanh.platform.security.KeycloakJwtRoles;
import com.congthanh.platform.temporal.TemporalWorkerRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class PaymentConfig {

    /** payment-service poll task queue "payment" (activity Payment.authorize/capture/refund). */
    @Bean
    TemporalWorkerRegistration paymentWorker(PaymentActivitiesImpl activities) {
        return new TemporalWorkerRegistration(TaskQueues.PAYMENT, w -> w.registerActivitiesImplementations(activities));
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Callback từ cổng thanh toán: xác thực bằng chữ ký trong provider, không có JWT.
                        .requestMatchers("/api/ecommerce/payment/vnpay/ipn", "/api/ecommerce/payment/paypal/**",
                                "/api/ecommerce/payment/webhooks/**", "/api/ecommerce/payment/sandbox/**").permitAll()
                        .requestMatchers("/api/ecommerce/payment/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtRoles())))
                .build();
    }
}
