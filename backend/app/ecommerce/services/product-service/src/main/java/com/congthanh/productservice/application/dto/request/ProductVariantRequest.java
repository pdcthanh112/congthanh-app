package com.congthanh.productservice.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    private String product;

    private String name;

    private String sku;

    private BigDecimal price;

    private List<CreateVariantImageRequest> image;

}
