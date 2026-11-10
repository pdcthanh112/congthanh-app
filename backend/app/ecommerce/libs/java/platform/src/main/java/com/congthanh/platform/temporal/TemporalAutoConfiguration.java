package com.congthanh.platform.temporal;

import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Temporal client + worker dựng bằng SDK thuần (không phụ thuộc temporal-spring-boot-starter, để không
 * bị kẹt version khi Spring Boot lên major). Metrics SDK đẩy vào Micrometer → /actuator/prometheus.
 */
@AutoConfiguration
@ConditionalOnClass(WorkflowClient.class)
@EnableConfigurationProperties(TemporalProperties.class)
public class TemporalAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TemporalAutoConfiguration.class);

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    WorkflowServiceStubs workflowServiceStubs(TemporalProperties props, ObjectProvider<MeterRegistry> meters) {
        WorkflowServiceStubsOptions.Builder options = WorkflowServiceStubsOptions.newBuilder().setTarget(props.target());
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            Scope scope = new RootScopeBuilder()
                    .reporter(new MicrometerClientStatsReporter(registry))
                    .reportEvery(Duration.ofSeconds(10));
            options.setMetricsScope(scope);
        }
        // Kết nối lười: service vẫn khởi động được khi Temporal chưa sẵn sàng; lệnh đầu tiên mới kết nối.
        return WorkflowServiceStubs.newServiceStubs(options.build());
    }

    @Bean
    @ConditionalOnMissingBean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs, TemporalProperties props) {
        return WorkflowClient.newInstance(stubs, WorkflowClientOptions.newBuilder().setNamespace(props.namespace()).build());
    }

    @Bean
    @ConditionalOnMissingBean
    WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    TemporalWorkers temporalWorkers(WorkerFactory factory, List<TemporalWorkerRegistration> registrations,
                                    TemporalProperties props) {
        return new TemporalWorkers(factory, registrations, props);
    }

    /** Start worker sau khi context sẵn sàng, stop trước khi datasource đóng. */
    static final class TemporalWorkers implements SmartLifecycle {
        private final WorkerFactory factory;
        private final List<TemporalWorkerRegistration> registrations;
        private final TemporalProperties props;
        private volatile boolean running;

        TemporalWorkers(WorkerFactory factory, List<TemporalWorkerRegistration> registrations, TemporalProperties props) {
            this.factory = factory;
            this.registrations = registrations;
            this.props = props;
        }

        @Override
        public void start() {
            if (!props.workersEnabled() || registrations.isEmpty()) {
                log.info("temporal workers disabled");
                return;
            }
            for (TemporalWorkerRegistration r : registrations) {
                r.register().accept(factory.newWorker(r.taskQueue()));
                log.info("temporal worker polling task queue '{}' on {}", r.taskQueue(), props.target());
            }
            factory.start();
            running = true;
        }

        @Override
        public void stop() {
            if (running) {
                factory.shutdown();
                factory.awaitTermination(props.shutdownGrace().toMillis(), TimeUnit.MILLISECONDS);
                running = false;
            }
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return Integer.MAX_VALUE - 1000;
        }
    }
}
