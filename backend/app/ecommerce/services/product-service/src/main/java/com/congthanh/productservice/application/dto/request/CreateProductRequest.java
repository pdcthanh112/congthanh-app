package com.congthanh.productservice.application.dto.request;

import com.congthanh.commonservice.exception.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tạo sản phẩm gốc, có thể kèm thuộc tính, ảnh và variant trong cùng một transaction. Ảnh là id media đã tải lên
 * media-service (không nhận file ở đây). {@code slug} bỏ trống thì sinh từ tên.
 */
public record CreateProductRequest(@NotBlank @Size(max = 255) String name,
                                   @Size(max = 255) String slug,
                                   @Size(max = 64) String sku,
                                   @Size(max = 14) String gtin,
                                   BigDecimal price,
                                   String currency,
                                   String description,
                                   @Size(max = 255) String supplier,
                                   Long brandId,
                                   boolean featured,
                                   String status,
                                   @NotEmpty List<@NotBlank String> categoryIds,
                                   List<@Valid ImageRequest> images,
                                   Long thumbnailId,
                                   List<@Valid AttributeRequest> attributes,
                                   List<@Valid CreateVariantRequest> variants) {

    public CreateProductRequest {
        images = images == null ? List.of() : images;
        attributes = attributes == null ? List.of() : attributes;
        variants = variants == null ? List.of() : variants;
    }

    /** Ảnh đã có trên media-service. */
    public record ImageRequest(@NotNull Long mediaId, @NotBlank @Size(max = 1024) String url, int displayOrder) {
    }

    /** Giá trị thông số: {@code value} cho thuộc tính một giá trị, {@code values} cho MULTI_SELECT (ADR 0033). */
    public record AttributeRequest(@NotNull Long attributeId, String value, @Size(max = 50) List<String> values) {

        /** Danh sách giá trị đã gộp; thiếu cả hai hoặc gửi cả hai thì 400. */
        public List<String> rawValues() {
            boolean hasValue = value != null && !value.isBlank();
            boolean hasValues = values != null && !values.isEmpty();
            if (hasValue && hasValues) {
                throw new BadRequestException("attribute " + attributeId + ": send either value or values, not both");
            }
            if (hasValue) {
                return List.of(value);
            }
            if (hasValues) {
                return List.copyOf(values);
            }
            throw new BadRequestException("attribute " + attributeId + ": value is required");
        }
    }
}
