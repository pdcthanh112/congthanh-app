package com.congthanh.productservice.application.dto.viewmodel;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailVm(
        String id,
        String name,
        String brandName,
        List<ProductCategoryVm> category,
        List<ProductAttributeVm> attribute,
        String description,
        Boolean isFeatured,
        Boolean hasVariant,
        BigDecimal price,
        String thumbnail,
        List<String> images,
        Double averageRating,
        Long reviewCount,
        Boolean inStock,
        List<ProductVariantVm> variants
) {
}
