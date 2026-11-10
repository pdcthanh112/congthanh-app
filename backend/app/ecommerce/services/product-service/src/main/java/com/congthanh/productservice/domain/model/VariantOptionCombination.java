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
@Table(name = "variant_option_combination")
public class VariantOptionCombination {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "variant_option_id", nullable = false)
    private VariantOption variantOption; //chỗ này đáng ra phải nối với VariantOptionValue, nhớ đọc và sửa lại
    // và không có trường "value"
    @Column(name = "display_order")
    private int displayOrder;

    private String value;
}
