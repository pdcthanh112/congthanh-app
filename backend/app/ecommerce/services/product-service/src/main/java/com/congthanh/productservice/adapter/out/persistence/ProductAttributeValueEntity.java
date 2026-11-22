package com.congthanh.productservice.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product_attribute_value")
class ProductAttributeValueEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private ProductEntity product;

    @ManyToOne
    @JoinColumn(name = "product_attribute_id", nullable = false)
    @ToString.Exclude
    private ProductAttributeEntity productAttribute;

    @Column(nullable = false)
    private String value;

    /** Chỉ có khi thuộc tính kiểu NUMBER. */
    @Column(name = "value_number")
    private BigDecimal valueNumber;
}
