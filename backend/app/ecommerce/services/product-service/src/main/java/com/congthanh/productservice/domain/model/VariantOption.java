package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;

/** Trục variant dùng chung cho cả catalog (Color, Size, Capacity). Tên unique không phân biệt hoa thường (ở DB). */
public record VariantOption(long id, String name) {

    public VariantOption {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        name = name.strip();
    }
}
