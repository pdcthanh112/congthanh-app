package com.congthanh.disputeservice.config;

import com.congthanh.disputeservice.application.port.in.DisputeDesk;
import com.congthanh.platform.security.KeycloakJwtRoles;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@EnableScheduling
class DisputeConfig {

    private static final Logger log = LoggerFactory.getLogger(DisputeConfig.class);
    private static final String BASE = "/api/ecommerce/disputes";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Tin cậy bằng chữ ký Stripe, không bằng JWT.
                        .requestMatchers(HttpMethod.POST, BASE + "/webhooks/stripe").permitAll()
                        .requestMatchers(HttpMethod.GET, BASE, BASE + "/**").hasAnyRole("FINANCE", "SUPPORT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, BASE, BASE + "/**").hasAnyRole("FINANCE", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtRoles())))
                .build();
    }

    @Bean
    DeadlineWatch deadlineWatch(DisputeDesk desk) {
        return new DeadlineWatch(desk);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));
        var backoff = new ExponentialBackOff(500, 2);
        backoff.setMaxElapsedTime(30_000);
        return new DefaultErrorHandler(recoverer, backoff);
    }

    /** Hạn nộp là cứng: log ERROR (gắn cảnh báo Loki/Grafana) cho tranh chấp còn mở sắp tới hạn. */
    static class DeadlineWatch {
        private final DisputeDesk desk;

        DeadlineWatch(DisputeDesk desk) {
            this.desk = desk;
        }

        @Scheduled(cron = "${disputes.deadline-cron:0 0 * * * *}")
        void check() {
            try {
                desk.dueSoon().forEach(d -> log.error("dispute {} ({} {}) evidence due {} still needs a response",
                        d.id(), d.amount(), d.currency(), d.evidenceDueBy()));
            } catch (RuntimeException e) {
                log.warn("dispute deadline check failed: {}", e.toString());
            }
        }
    }
}
