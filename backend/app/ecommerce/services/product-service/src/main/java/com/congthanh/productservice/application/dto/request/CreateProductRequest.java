package com.congthanh.productservice.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductRequest {

    @NotNull
    private String name;
    @NotNull
    private List<String> category;
    @NotNull
    private String slug;
    private String sku;
    private String gtin;
    private BigDecimal price;
    private String description;
    private String supplier;
    private String brand;
    @NotNull
    private MultipartFile thumbnail;
    private List<MultipartFile> image;
    private List<ProductAttributeRequest> attribute;
    private List<ProductVariantRequest> variant;
    private Boolean isFeatured;
    private MetadataRequest metadata;


    record ProductAttributeRequest(@NotNull String attribute, @NotNull String value) {
    }

    record ProductVariantRequest(@NotNull String sku, @NotNull String gtin, @NotNull BigDecimal price) {
    }
}
