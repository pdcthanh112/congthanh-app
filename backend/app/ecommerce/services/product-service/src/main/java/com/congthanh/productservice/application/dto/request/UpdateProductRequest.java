package com.congthanh.productservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sửa thông tin sản phẩm. Trường null giữ nguyên giá trị cũ; {@code categoryIds} khác null thì thay toàn bộ danh mục.
 * {@code version} là khoá lạc quan lấy từ lần đọc trước: lệch thì trả 409. Trạng thái đổi qua endpoint riêng.
 */
public record UpdateProductRequest(@NotBlank String id,
                                   @NotNull Long version,
                                   @Size(max = 255) String name,
                                   @Size(max = 255) String slug,
                                   @Size(max = 64) String sku,
                                   @Size(max = 14) String gtin,
                                   BigDecimal price,
                                   String currency,
                                   String description,
                                   @Size(max = 255) String supplier,
                                   Long brandId,
                                   Boolean featured,
                                   Long thumbnailId,
                                   List<@NotBlank String> categoryIds) {
}
