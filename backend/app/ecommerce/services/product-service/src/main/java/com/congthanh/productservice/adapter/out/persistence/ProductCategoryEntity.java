package com.congthanh.productservice.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product_categories")
class ProductCategoryEntity {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    @ToString.Exclude
    private ProductEntity product;

    @Column(name = "category_id", nullable = false, updatable = false)
    private String categoryId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
