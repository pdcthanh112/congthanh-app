package com.congthanh.productservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(CategoryAttributeEntity.Key.class)
@Table(name = "category_attribute")
class CategoryAttributeEntity {

    @Id
    @Column(name = "category_id")
    private String categoryId;

    @Id
    @Column(name = "product_attribute_id")
    private Long attributeId;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Khoá ghép (category_id, product_attribute_id); JPA cần class có constructor rỗng cho @IdClass. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    static class Key implements Serializable {
        private String categoryId;
        private Long attributeId;
    }
}
