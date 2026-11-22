package com.congthanh.productservice.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

/** Giá trị được phép của một trục variant trên một sản phẩm gốc (Black, 128 GB). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "variant_option_value")
class VariantOptionValueEntity {

    @Id
    private Long id;

    /** Sản phẩm gốc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private ProductEntity product;

    @ManyToOne
    @JoinColumn(name = "variant_option_id", nullable = false)
    private VariantOptionEntity variantOption;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private String value;
}
