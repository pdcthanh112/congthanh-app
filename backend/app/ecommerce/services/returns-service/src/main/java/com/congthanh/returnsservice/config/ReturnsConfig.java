package com.congthanh.returnsservice.config;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.platform.security.KeycloakJwtRoles;
import com.congthanh.platform.temporal.TemporalWorkerRegistration;
import com.congthanh.returnsservice.adapter.in.workflow.ReturnActivitiesImpl;
import com.congthanh.returnsservice.adapter.in.workflow.ReturnRefundWorkflowImpl;
import com.congthanh.returnsservice.application.port.in.ReturnAdmin;
import com.congthanh.returnsservice.domain.model.ReturnPolicy;
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
class ReturnsConfig {

    private static final Logger log = LoggerFactory.getLogger(ReturnsConfig.class);
    private static final String BASE = "/api/ecommerce/returns";

    @Bean
    ReturnPolicy returnPolicy(ReturnsProperties p) {
        return new ReturnPolicy(p.window(), p.restockingFeePercent(), p.autoApprove());
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        String[] staff = {"ADMIN", "SUPPORT", "WAREHOUSE"};
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, BASE).authenticated()
                        .requestMatchers(HttpMethod.GET, BASE + "/mine").authenticated()
                        .requestMatchers(HttpMethod.POST, BASE + "/*/cancel").authenticated()
                        .requestMatchers(HttpMethod.POST, BASE + "/*/approve", BASE + "/*/reject").hasAnyRole("ADMIN", "SUPPORT")
                        .requestMatchers(HttpMethod.POST, BASE + "/*/receive").hasAnyRole(staff)
                        .requestMatchers(HttpMethod.POST, BASE + "/*/retry-refund").hasAnyRole("ADMIN", "SUPPORT")
                        .requestMatchers(HttpMethod.GET, BASE).hasAnyRole(staff)
                        .requestMatchers(HttpMethod.GET, BASE + "/*").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtRoles())))
                .build();
    }

    @Bean
    TemporalWorkerRegistration returnsWorker(ReturnActivitiesImpl activities) {
        return new TemporalWorkerRegistration(TaskQueues.RETURNS, worker -> {
            worker.registerWorkflowImplementationTypes(ReturnRefundWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
        });
    }

    @Bean
    StuckRefundSweeper stuckRefundSweeper(ReturnAdmin admin) {
        return new StuckRefundSweeper(admin);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));
        var backoff = new ExponentialBackOff(500, 2);
        backoff.setMaxElapsedTime(30_000);
        return new DefaultErrorHandler(recoverer, backoff);
    }

    static class StuckRefundSweeper {
        private final ReturnAdmin admin;

        StuckRefundSweeper(ReturnAdmin admin) {
            this.admin = admin;
        }

        @Scheduled(fixedDelayString = "${returns.stuck-refund-sweep:PT5M}", initialDelayString = "PT1M")
        void sweep() {
            try {
                int n = admin.resumeStuckRefunds();
                if (n > 0) {
                    log.info("restarted refund workflow for {} stuck returns", n);
                }
            } catch (RuntimeException e) {
                log.warn("stuck refund sweep failed: {}", e.toString());
            }
        }
    }
}
