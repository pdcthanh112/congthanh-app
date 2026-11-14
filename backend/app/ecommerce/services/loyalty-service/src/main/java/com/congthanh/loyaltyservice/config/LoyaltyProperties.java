package com.congthanh.loyaltyservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param returnWindow    điểm thành khả dụng sau ngày giao + khoảng này (khớp hạn trả hàng của returns-service)
 * @param fallbackConfirm không có ngày giao (giao lỗi sự kiện) thì xác nhận sau khoảng này từ lúc tích
 * @param lotLifetime     điểm hết hạn sau khoảng này kể từ lúc khả dụng
 * @param pointValueUsd   giá trị một điểm khi đổi
 * @param minRedeem       số điểm tối thiểu mỗi lần đổi
 */
@ConfigurationProperties("loyalty")
public record LoyaltyProperties(@DefaultValue("P30D") Duration returnWindow, @DefaultValue("P45D") Duration fallbackConfirm,
                                @DefaultValue("P365D") Duration lotLifetime, @DefaultValue("0.01") BigDecimal pointValueUsd,
                                @DefaultValue("100") long minRedeem) {
}
