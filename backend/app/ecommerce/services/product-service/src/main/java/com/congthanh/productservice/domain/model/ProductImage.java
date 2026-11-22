package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;

/** Ảnh của sản phẩm; {@code mediaId} là id do media-service cấp (unique toàn hệ thống). */
public record ProductImage(long mediaId, String url, int displayOrder) {

    public ProductImage {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("image url is required");
        }
        if (url.length() > 1024) {
            throw new BadRequestException("image url is too long");
        }
        url = url.strip();
    }
}
