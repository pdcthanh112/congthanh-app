package com.congthanh.subscriptionservice.config;

import com.congthanh.platform.security.KeycloakJwtRoles;
import com.congthanh.subscriptionservice.application.port.in.SubscriptionUseCases;
import com.congthanh.subscriptionservice.domain.model.Subscription;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.subscriptionservice.application.port.out.Ids;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableScheduling
class SubscriptionConfig {

    @Bean
    Ids ids() {
        return Snowflake::nextString;
    }

    private static final Logger log = LoggerFactory.getLogger(SubscriptionConfig.class);
    private static final String BASE = "/api/ecommerce/subscriptions";

    @Bean
    Subscription.Schedule subscriptionSchedule() {
        return Subscription.Schedule.defaults();
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, BASE).hasAnyRole("ADMIN", "SUPPORT")
                        .requestMatchers(BASE, BASE + "/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtRoles())))
                .build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));
        var backoff = new ExponentialBackOff(500, 2);
        backoff.setMaxElapsedTime(30_000);
        return new DefaultErrorHandler(recoverer, backoff);
    }

    @Bean
    Ticker subscriptionTicker(SubscriptionUseCases subs) {
        return new Ticker(subs);
    }

    static class Ticker {
        private final SubscriptionUseCases subs;

        Ticker(SubscriptionUseCases subs) {
            this.subs = subs;
        }

        @Scheduled(fixedDelayString = "${subscriptions.tick-interval:PT5M}", initialDelayString = "PT1M")
        void tick() {
            try {
                int n = subs.tick();
                if (n > 0) {
                    log.info("subscription tick handled {} subscriptions", n);
                }
            } catch (RuntimeException e) {
                log.warn("subscription tick failed: {}", e.toString());
            }
        }
    }
}
