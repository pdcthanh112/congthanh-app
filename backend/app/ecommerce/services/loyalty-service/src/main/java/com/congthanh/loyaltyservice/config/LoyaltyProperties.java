package com.congthanh.loyaltyservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param returnWindow    điểm thành khả dụng sau ngày giao + khoảng này (khớp hạn trả hàng của returns-service)
 * @param fallbackConfirm không có ngày giao (giao lỗi sự kiện) thì xác nhận sau khoảng này từ lúc tích
 * @param lotLifetime     điểm hết hạn sau khoảng này kể từ lúc khả dụng
 * @param currency        đơn vị tiền (ISO 4217) của chương trình điểm: chỉ đơn hàng dùng đơn vị này mới tích/đổi điểm;
 *                        bắt buộc cấu hình, không có mặc định
 * @param pointValue      giá trị một điểm khi đổi, tính theo {@code currency}; bắt buộc cấu hình
 * @param minRedeem       số điểm tối thiểu mỗi lần đổi
 */
@Validated
@ConfigurationProperties("loyalty")
public record LoyaltyProperties(@DefaultValue("P30D") Duration returnWindow, @DefaultValue("P45D") Duration fallbackConfirm,
                                @DefaultValue("P365D") Duration lotLifetime, @NotBlank String currency,
                                @NotNull @Positive BigDecimal pointValue,
                                @DefaultValue("100") long minRedeem) {
}
