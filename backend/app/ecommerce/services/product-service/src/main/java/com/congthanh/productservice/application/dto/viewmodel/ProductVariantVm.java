package com.congthanh.productservice.application.dto.viewmodel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductVariantVm(String id,
                               String name,
                               String slug,
                               String sku,
                               String gtin,
                               BigDecimal price,
                               ProductImageVm thumbnail,
                               List<ProductImageVm> image,
                               Map<Long, String> options) {
}
