package com.congthanh.productservice.application.readmodel;

import com.congthanh.productservice.domain.model.enums.ProductStatus;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    @Id
    private String id;

    private String name;

    private List<ProductCategory> category;

    @Indexed(unique = true)
    private String slug;

    private String sku;

    private String gtin;

    @Indexed(name = "has_variant")
    private boolean hasVariant;

    private String description;

    private BigDecimal price;

    private String thumbnail;

    private List<ProductImage> image = new ArrayList<>();

    private List<ProductAttributeDocument> attribute = new ArrayList<>();

    private String supplier;

    private String brand;

    @Indexed(name = "is_featured")
    private boolean isFeatured;

    private ProductStatus status;

    record ProductCategory(Long id, String categoryId, @Indexed(name = "display_order") int displayOrder) {
    }

    record ProductImage(Long id, @Indexed(name = "image_path") String imagePath) {
    }

    record ProductAttributeDocument(Long id, String attribute, String value) {
    }
}