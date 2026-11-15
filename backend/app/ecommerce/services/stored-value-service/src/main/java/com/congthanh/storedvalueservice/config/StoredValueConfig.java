package com.congthanh.storedvalueservice.config;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.platform.security.KeycloakJwtRoles;
import com.congthanh.platform.temporal.TemporalWorkerRegistration;
import com.congthanh.storedvalueservice.adapter.in.workflow.StoredValueActivitiesImpl;
import com.congthanh.storedvalueservice.application.port.in.StoredValueUseCases;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.storedvalueservice.application.port.out.Ids;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableScheduling
class StoredValueConfig {

    @Bean
    Ids ids() {
        return Snowflake::nextString;
    }

    private static final Logger log = LoggerFactory.getLogger(StoredValueConfig.class);
    private static final String BASE = "/api/ecommerce/stored-value";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, BASE + "/me", BASE + "/accounts/*/history").authenticated()
                        .requestMatchers(HttpMethod.GET, BASE + "/customers/*").hasAnyRole("ADMIN", "SUPPORT", "FINANCE")
                        .requestMatchers(HttpMethod.POST, BASE + "/credits").hasAnyRole("ADMIN", "SUPPORT")
                        .requestMatchers(HttpMethod.POST, BASE + "/accounts/*/freeze", BASE + "/accounts/*/unfreeze")
                        .hasAnyRole("ADMIN", "FINANCE")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtRoles())))
                .build();
    }

    @Bean
    TemporalWorkerRegistration storedValueWorker(StoredValueActivitiesImpl activities) {
        return new TemporalWorkerRegistration(TaskQueues.STORED_VALUE, w -> w.registerActivitiesImplementations(activities));
    }

    @Bean
    ExpiredHoldSweeper expiredHoldSweeper(StoredValueUseCases storedValue) {
        return new ExpiredHoldSweeper(storedValue);
    }

    static class ExpiredHoldSweeper {
        private final StoredValueUseCases storedValue;

        ExpiredHoldSweeper(StoredValueUseCases storedValue) {
            this.storedValue = storedValue;
        }

        @Scheduled(fixedDelayString = "${stored-value.hold-sweep:PT5M}", initialDelayString = "PT1M")
        void sweep() {
            try {
                int n = storedValue.releaseExpired();
                if (n > 0) {
                    log.info("released {} expired holds", n);
                }
            } catch (RuntimeException e) {
                log.warn("expired hold sweep failed: {}", e.toString());
            }
        }
    }
}
