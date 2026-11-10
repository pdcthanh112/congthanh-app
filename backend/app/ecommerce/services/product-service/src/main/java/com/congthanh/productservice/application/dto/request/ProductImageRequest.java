package com.congthanh.productservice.application.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductImageRequest {
    private Long id;
    private String imagePath;
    private int displayOrder;
}
