package com.congthanh.productservice.application.mapper;

import com.congthanh.productservice.application.dto.ProductAttributeDTO;
import com.congthanh.productservice.domain.model.ProductAttribute;

public final class ProductAttributeMapper {

    private ProductAttributeMapper() {
    }

    public static ProductAttributeDTO toDto(ProductAttribute a) {
        return ProductAttributeDTO.builder()
                .id(a.id())
                .code(a.code())
                .name(a.name())
                .dataType(a.dataType())
                .unit(a.unit())
                .filterable(a.filterable())
                .searchable(a.searchable())
                .displayOrder(a.displayOrder())
                .build();
    }
}
