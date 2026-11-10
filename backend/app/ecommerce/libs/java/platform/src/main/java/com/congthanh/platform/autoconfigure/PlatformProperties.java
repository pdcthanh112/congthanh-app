package com.congthanh.platform.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("platform")
public record PlatformProperties(Outbox outbox, Idempotency idempotency) {

    public PlatformProperties {
        outbox = outbox == null ? new Outbox(null) : outbox;
        idempotency = idempotency == null ? new Idempotency(true, List.of(), false, null) : idempotency;
    }

    /** @param source tên service ghi vào EventEnvelope.source; mặc định spring.application.name. */
    public record Outbox(String source) {
    }

    /**
     * @param paths    các path pattern (không gồm context-path) áp dụng Idempotency-Key, ví dụ /ecommerce/orders
     * @param required true: thiếu header thì trả 400
     * @param ttl      giữ response bao lâu để replay; mặc định 24h
     */
    public record Idempotency(boolean enabled, List<String> paths, boolean required, Duration ttl) {
        public Idempotency {
            paths = paths == null ? List.of() : paths;
            ttl = ttl == null ? Duration.ofHours(24) : ttl;
        }
    }
}
