package com.congthanh.platform.autoconfigure;

import com.congthanh.platform.idempotency.IdempotencyKeyFilter;
import com.congthanh.platform.idempotency.IdempotencyStore;
import com.congthanh.platform.inbox.ProcessedEvents;
import com.congthanh.platform.migration.LiquibaseSchemaCreator;
import com.congthanh.platform.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Tự cấu hình khối nền cho service Java: outbox, consumer chống trùng, Idempotency-Key,
 * và tạo schema của service trước khi Liquibase chạy. Changelog của các bảng đó nằm ở
 * {@code classpath:db/platform/changelog.yaml}, master changelog của service include nó.
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlatformAutoConfiguration.class);
    @Bean
    @ConditionalOnMissingBean
    Clock platformClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(DataSource dataSource, PlatformProperties props, Environment env, Clock clock) {
        String source = props.outbox().source() != null
                ? props.outbox().source()
                : env.getProperty("spring.application.name", "unknown-service");
        return new OutboxWriter(JdbcClient.create(dataSource), source, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    ProcessedEvents processedEvents(DataSource dataSource) {
        return new ProcessedEvents(JdbcClient.create(dataSource));
    }

    @Bean
    @ConditionalOnClass(name = "liquibase.integration.spring.SpringLiquibase")
    static LiquibaseSchemaCreator liquibaseSchemaCreator() {
        return new LiquibaseSchemaCreator();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBooleanProperty(name = "platform.idempotency.enabled", matchIfMissing = true)
    @EnableScheduling
    static class IdempotencyConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdempotencyStore idempotencyStore(DataSource dataSource, PlatformProperties props, Clock clock) {
            return new IdempotencyStore(JdbcClient.create(dataSource), props.idempotency().ttl(), clock);
        }

        /** Chạy sau Spring Security (order -100) để biết principal. */
        @Bean
        FilterRegistrationBean<IdempotencyKeyFilter> idempotencyKeyFilter(IdempotencyStore store, PlatformProperties props) {
            var reg = new FilterRegistrationBean<>(new IdempotencyKeyFilter(
                    store, props.idempotency().paths(), props.idempotency().required()));
            reg.setOrder(0);
            reg.setEnabled(!props.idempotency().paths().isEmpty());
            return reg;
        }

        @Bean
        IdempotencyJanitor idempotencyJanitor(ObjectProvider<IdempotencyStore> store) {
            return new IdempotencyJanitor(store);
        }
    }

    static class IdempotencyJanitor {
        private final ObjectProvider<IdempotencyStore> store;

        IdempotencyJanitor(ObjectProvider<IdempotencyStore> store) {
            this.store = store;
        }

        @Scheduled(fixedDelayString = "${platform.idempotency.purge-interval:PT1H}", initialDelayString = "PT5M")
        void purge() {
            int n = store.getObject().purgeExpired();
            if (n > 0) {
                log.info("purged {} expired idempotency keys", n);
            }
        }
    }
}
