package com.congthanh.platform.temporal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param target         host:port của Temporal frontend
 * @param namespace      namespace (mỗi môi trường một namespace)
 * @param workersEnabled false: chỉ dùng client (ví dụ trong test hoặc pod chỉ phục vụ API)
 * @param shutdownGrace  chờ activity đang chạy xong khi tắt pod
 */
@ConfigurationProperties("platform.temporal")
public record TemporalProperties(String target, String namespace, Boolean workersEnabled, Duration shutdownGrace) {

    public TemporalProperties {
        target = target == null ? "localhost:7233" : target;
        namespace = namespace == null ? "default" : namespace;
        workersEnabled = workersEnabled == null || workersEnabled;
        shutdownGrace = shutdownGrace == null ? Duration.ofSeconds(25) : shutdownGrace;
    }
}
