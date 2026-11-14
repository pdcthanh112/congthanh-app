package com.congthanh.commonservice.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("common")
public record CommonCoreProperties(@DefaultValue Problems problems, @DefaultValue Logging logging,
                                   @DefaultValue Cors cors, @DefaultValue Audit audit) {

    /** {@code type-base}: tiền tố URI của trường {@code type} trong body lỗi. */
    public record Problems(@DefaultValue("https://congthanhapp.com/problems/") String typeBase) {
    }

    /**
     * {@code requests-enabled}: bật access log. {@code exclude-paths}: đường dẫn không ghi log.
     * {@code slow-query-threshold}: Hibernate ghi log câu SQL chạy lâu hơn ngưỡng này; 0 là tắt.
     */
    public record Logging(@DefaultValue("true") boolean requestsEnabled,
                          @DefaultValue("/actuator/**") List<String> excludePaths,
                          @DefaultValue("500ms") Duration slowQueryThreshold) {
    }

    /** CORS chỉ bật khi {@code allowed-origins} có giá trị; mặc định để gateway (APISIX) lo. */
    public record Cors(@DefaultValue List<String> allowedOrigins,
                       @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) List<String> allowedMethods,
                       @DefaultValue("*") List<String> allowedHeaders,
                       @DefaultValue List<String> exposedHeaders,
                       @DefaultValue("false") boolean allowCredentials,
                       @DefaultValue("1h") Duration maxAge) {
    }

    /** {@code system-auditor}: giá trị created_by / updated_by khi không có người dùng đăng nhập. */
    public record Audit(@DefaultValue("system") String systemAuditor) {
    }
}
