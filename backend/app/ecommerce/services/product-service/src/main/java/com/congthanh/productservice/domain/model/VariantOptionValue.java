package com.congthanh.productservice.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "variant_option_value")
public class VariantOptionValue {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "variant_option_id", nullable = false)
    private VariantOption variantOption;

    @Column(name = "display_order")
    private int displayOrder;

    private String value;
}
