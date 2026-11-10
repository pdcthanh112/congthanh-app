package com.congthanh.productservice.application.dto;

import com.congthanh.productservice.domain.model.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private String slug;

    private List<ProductCategoryDTO> category;

    private boolean hasVariant;

    private String description;

    private String thumbnail;

    private List<ProductImageDTO> image;

    private List<ProductAttributeValueDTO> attribute;

    private String supplier;

    private String brand;

    private ProductStatus status;
}
