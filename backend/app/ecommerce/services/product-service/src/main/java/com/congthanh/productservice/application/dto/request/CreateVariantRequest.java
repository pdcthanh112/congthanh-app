package com.congthanh.productservice.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tạo variant dưới một sản phẩm gốc. {@code options}: đúng một giá trị cho mỗi trục; giá trị chưa khai báo trên
 * sản phẩm gốc sẽ được thêm. Mọi variant của cùng sản phẩm gốc phải dùng cùng bộ trục.
 */
public record CreateVariantRequest(String name,
                                   @NotBlank String sku,
                                   String gtin,
                                   BigDecimal price,
                                   String currency,
                                   String status,
                                   @NotEmpty List<@Valid OptionSelection> options) {

    public record OptionSelection(@NotNull Long optionId, @NotBlank String value) {
    }
}
