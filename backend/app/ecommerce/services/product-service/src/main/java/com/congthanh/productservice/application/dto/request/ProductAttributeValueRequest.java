package com.congthanh.productservice.application.dto.request;

import com.congthanh.commonservice.exception.BadRequestException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeValueRequest {

    /** Id thuộc tính. */
    @NotNull
    private Long attribute;

    @NotBlank
    private String product;

    /** Một giá trị (thuộc tính TEXT/NUMBER/BOOLEAN). Gửi {@code value} hoặc {@code values}, không cả hai. */
    private String value;

    /** Nhiều giá trị cho thuộc tính MULTI_SELECT (ADR 0033). */
    @Size(max = 50)
    private List<String> values;

    /** Danh sách giá trị đã gộp từ {@code value}/{@code values}; thiếu cả hai hoặc gửi cả hai thì 400. */
    public List<String> rawValues() {
        boolean hasValue = value != null && !value.isBlank();
        boolean hasValues = values != null && !values.isEmpty();
        if (hasValue && hasValues) {
            throw new BadRequestException("send either value or values, not both");
        }
        if (hasValue) {
            return List.of(value);
        }
        if (hasValues) {
            return List.copyOf(values);
        }
        throw new BadRequestException("value is required");
    }
}
