package com.congthanh.productservice.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));
        var backoff = new ExponentialBackOff(500, 2);
        backoff.setMaxElapsedTime(30_000);
        var handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setBackOffFunction((record, ex) -> isOutage(ex) ? new FixedBackOff(5_000, FixedBackOff.UNLIMITED_ATTEMPTS) : null);
        return handler;
    }

    private static boolean isOutage(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof DataAccessResourceFailureException
                    || t instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException
                    || t instanceof io.grpc.StatusRuntimeException) {
                return true;
            }
        }
        return false;
    }
}
