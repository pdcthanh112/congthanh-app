package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.ProductCategoryDTO;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.ProductImageDTO;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class ProductDtoMapper {

    private ProductDtoMapper() {
    }

    static ProductDTO product(ProductEntity p) {
        List<ProductImageDTO> images = p.getImage().stream()
                .sorted(Comparator.comparingInt(ProductImageEntity::getDisplayOrder).thenComparing(ProductImageEntity::getId))
                .map(ProductDtoMapper::image)
                .toList();
        String thumbnail = images.stream()
                .filter(i -> Objects.equals(i.getId(), p.getThumbnail()))
                .findFirst()
                .or(() -> images.stream().findFirst())
                .map(ProductImageDTO::getImagePath)
                .orElse(null);
        List<ProductAttributeValueDTO> attributes = p.getAttribute().stream()
                .sorted(Comparator.comparingInt((ProductAttributeValueEntity v) -> v.getProductAttribute().getDisplayOrder())
                        .thenComparing(v -> v.getProductAttribute().getName()))
                .map(ProductDtoMapper::attributeValue)
                .toList();
        List<ProductCategoryDTO> categories = p.getCategory().stream()
                .sorted(Comparator.comparingInt(ProductCategoryEntity::getDisplayOrder))
                .map(c -> new ProductCategoryDTO(c.getId(), p.getId(), c.getCategoryId(), c.getDisplayOrder()))
                .toList();
        return ProductDTO.builder()
                .id(p.getId())
                .parentId(p.getParent() == null ? null : p.getParent().getId())
                .name(p.getName())
                .slug(p.getSlug())
                .sku(p.getSku())
                .gtin(p.getGtin())
                .price(p.getPrice())
                .currency(p.getCurrency())
                .category(categories)
                .hasVariant(p.isHasVariant())
                .featured(p.isFeatured())
                .description(p.getDescription())
                .thumbnail(thumbnail)
                .image(images)
                .attribute(attributes)
                .supplier(p.getSupplier())
                .brand(p.getBrand() == null ? null : String.valueOf(p.getBrand()))
                .status(p.getStatus())
                .version(p.getVersion())
                .build();
    }

    static ProductImageDTO image(ProductImageEntity i) {
        return ProductImageDTO.builder()
                .id(i.getId())
                .product(i.getProduct() == null ? null : i.getProduct().getId())
                .imagePath(i.getImagePath())
                .displayOrder(i.getDisplayOrder())
                .isDefault(i.isDefault())
                .build();
    }

    static ProductAttributeValueDTO attributeValue(ProductAttributeValueEntity v) {
        ProductAttributeEntity a = v.getProductAttribute();
        return ProductAttributeValueDTO.builder()
                .id(v.getId())
                .attribute(a.getName())
                .attributeId(a.getId())
                .attributeCode(a.getCode())
                .unit(a.getUnit())
                .product(v.getProduct() == null ? null : v.getProduct().getId())
                .value(v.getValue())
                .valueNumber(v.getValueNumber())
                .build();
    }
}
