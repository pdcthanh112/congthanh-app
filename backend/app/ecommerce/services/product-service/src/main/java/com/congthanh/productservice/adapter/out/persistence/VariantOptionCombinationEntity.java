package com.congthanh.productservice.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Variant chọn một giá trị trên một trục. FK ba cột (giá trị, trục, sản phẩm gốc) bảo đảm giá trị thuộc đúng trục và
 * đúng sản phẩm gốc; unique (variant, trục) bảo đảm mỗi trục chỉ một giá trị.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "variant_option_combination")
class VariantOptionCombinationEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    @ToString.Exclude
    private ProductEntity variant;

    /** Sản phẩm gốc của variant (lặp lại để làm FK ba cột). */
    @JdbcTypeCode(SqlTypes.BIGINT)
    @Column(name = "parent_id", nullable = false)
    private String parentId;

    @ManyToOne
    @JoinColumn(name = "variant_option_id", nullable = false)
    private VariantOptionEntity variantOption;

    @ManyToOne
    @JoinColumn(name = "variant_option_value_id", nullable = false)
    private VariantOptionValueEntity variantOptionValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public String value() {
        return variantOptionValue == null ? null : variantOptionValue.getValue();
    }
}
