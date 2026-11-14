package com.congthanh.returnsservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties("returns")
public record ReturnsProperties(@DefaultValue("P30D") Duration window,
                                @DefaultValue("0") BigDecimal restockingFeePercent,
                                @DefaultValue("true") boolean autoApprove) {
}
