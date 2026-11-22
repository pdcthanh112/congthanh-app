package com.congthanh.productservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @NotBlank
    private String value;
}
