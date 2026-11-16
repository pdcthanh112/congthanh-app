package com.congthanh.loyaltyservice.config;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.loyaltyservice.adapter.in.workflow.LoyaltyActivitiesImpl;
import com.congthanh.loyaltyservice.application.port.in.LoyaltyUseCases;
import com.congthanh.loyaltyservice.domain.model.Member;
import com.congthanh.platform.security.KeycloakJwtRoles;
import com.congthanh.platform.temporal.TemporalWorkerRegistration;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.loyaltyservice.application.port.out.Ids;
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
class LoyaltyConfig {

    /** Snowflake dùng chung trong common-core (BIGINT, node theo NODE_ID). */
    @Bean
    Ids ids() {
        return Snowflake::nextString;
    }

    private static final Logger log = LoggerFactory.getLogger(LoyaltyConfig.class);
    private static final String BASE = "/api/ecommerce/loyalty";

    @Bean
    Member.Rules loyaltyRules(LoyaltyProperties p) {
        return new Member.Rules(p.returnWindow(), p.fallbackConfirm(), p.lotLifetime(), p.currency().trim().toUpperCase(java.util.Locale.ROOT), p.pointValue(), p.minRedeem());
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, BASE + "/me").authenticated()
                        .requestMatchers(HttpMethod.GET, BASE + "/customers/*").hasAnyRole("ADMIN", "SUPPORT")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtRoles())))
                .build();
    }

    @Bean
    TemporalWorkerRegistration loyaltyWorker(LoyaltyActivitiesImpl activities) {
        return new TemporalWorkerRegistration(TaskQueues.LOYALTY, w -> w.registerActivitiesImplementations(activities));
    }

    @Bean
    Housekeeping loyaltyHousekeeping(LoyaltyUseCases loyalty) {
        return new Housekeeping(loyalty);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));
        var backoff = new ExponentialBackOff(500, 2);
        backoff.setMaxElapsedTime(30_000);
        return new DefaultErrorHandler(recoverer, backoff);
    }

    static class Housekeeping {
        private final LoyaltyUseCases loyalty;

        Housekeeping(LoyaltyUseCases loyalty) {
            this.loyalty = loyalty;
        }

        @Scheduled(fixedDelayString = "${loyalty.housekeeping-interval:PT1H}", initialDelayString = "PT2M")
        void run() {
            try {
                int n = loyalty.housekeeping();
                if (n > 0) {
                    log.info("loyalty housekeeping processed {} members", n);
                }
            } catch (RuntimeException e) {
                log.warn("loyalty housekeeping failed: {}", e.toString());
            }
        }
    }
}
