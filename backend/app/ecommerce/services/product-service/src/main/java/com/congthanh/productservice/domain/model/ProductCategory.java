package com.congthanh.productservice.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product_categories")
public class ProductCategory {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Product product;

    @Column(name = "category_id", nullable = false, updatable = false)
    private String categoryId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
