package com.congthanh.platform.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("platform")
public record PlatformProperties(Outbox outbox, Idempotency idempotency) {

    public PlatformProperties {
        outbox = outbox == null ? new Outbox(null, null, null) : outbox;
        idempotency = idempotency == null ? new Idempotency(true, List.of(), false, null, null) : idempotency;
    }

    /**
     * @param source             tên service ghi vào EventEnvelope.source; mặc định spring.application.name
     * @param retention          giữ hàng outbox bao lâu sau khi ghi (Debezium đọc qua WAL); mặc định 1h
     * @param processedRetention giữ processed_events bao lâu, phải dài hơn retention của topic; mặc định 14 ngày
     */
    public record Outbox(String source, Duration retention, Duration processedRetention) {
        public Outbox {
            retention = retention == null ? Duration.ofHours(1) : retention;
            processedRetention = processedRetention == null ? Duration.ofDays(14) : processedRetention;
        }
    }

    /**
     * @param paths    các path pattern (không gồm context-path) áp dụng Idempotency-Key, ví dụ /ecommerce/orders
     * @param required true: thiếu header thì trả 400
     * @param ttl      giữ response bao lâu để replay; mặc định 24h
     * @param lease    request đầu giữ key IN_PROGRESS tối đa bao lâu; quá hạn (pod chết) thì request gửi lại được
     *                 tiếp quản. Phải dài hơn thời gian xử lý lâu nhất của endpoint; mặc định 60s
     */
    public record Idempotency(boolean enabled, List<String> paths, boolean required, Duration ttl, Duration lease) {
        public Idempotency {
            paths = paths == null ? List.of() : paths;
            ttl = ttl == null ? Duration.ofHours(24) : ttl;
            lease = lease == null ? Duration.ofSeconds(60) : lease;
        }
    }
}
