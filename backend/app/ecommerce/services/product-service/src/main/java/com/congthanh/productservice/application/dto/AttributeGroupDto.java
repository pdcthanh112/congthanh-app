package com.congthanh.productservice.application.dto;

import com.congthanh.productservice.domain.model.AttributeGroup;

/** Nhóm hiển thị của thông số. */
public record AttributeGroupDto(Long id, String name, int displayOrder) {

    public static AttributeGroupDto of(AttributeGroup g) {
        return new AttributeGroupDto(g.id(), g.name(), g.displayOrder());
    }
}
