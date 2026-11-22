package com.congthanh.productservice.adapter.out.persistence;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sản phẩm gốc ({@code parent == null}) hoặc variant (đúng một cấp dưới sản phẩm gốc). Variant là một sản phẩm đầy
 * đủ (SKU, giá, ảnh, tồn kho riêng); tổ hợp giá trị option của nó nằm ở {@link VariantOptionCombinationEntity} và được
 * tóm vào {@link #variantKey} để DB chặn hai variant trùng tổ hợp (ADR 0030).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "products")
class ProductEntity {

    @JdbcTypeCode(SqlTypes.BIGINT)
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String slug;

    private String sku;

    private String gtin;

    @Column(name = "has_variant", nullable = false)
    private boolean hasVariant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @ToString.Exclude
    private ProductEntity parent;

    @OneToMany(mappedBy = "parent")
    @JsonIgnore
    @ToString.Exclude
    @Builder.Default
    private List<ProductEntity> variant = new ArrayList<>();

    /** Id các giá trị option đã chọn, sắp tăng dần, nối '-'; chỉ variant mới có. */
    @Column(name = "variant_key")
    private String variantKey;

    /** Giá niêm yết gốc; giá bán theo bảng giá thuộc pricing-service. */
    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR) // character(3): thiếu dòng này Hibernate validate báo sai kiểu (bpchar ≠ varchar)
    @Column(name = "currency", length = 3, columnDefinition = "character(3)")
    private String currency;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @ToString.Exclude
    @Builder.Default
    private List<ProductCategoryEntity> category = new ArrayList<>();

    private String supplier;

    private Long brand;

    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured;

    @Column(columnDefinition = "text")
    private String description;

    private Long thumbnail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.INACTIVE;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude
    @Builder.Default
    private Set<ProductImageEntity> image = new HashSet<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude
    @Builder.Default
    private Set<ProductAttributeValueEntity> attribute = new HashSet<>();

    /** Khoá lạc quan. Null nghĩa là chưa lưu (Spring Data dùng để chọn persist thay vì merge). */
    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isVariant() {
        return parent != null;
    }
}
