package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.commonservice.model.BaseEntity;
import com.congthanh.productservice.domain.model.enums.AttributeDataType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product_attribute")
class ProductAttributeEntity extends BaseEntity {

    @Id
    private Long id;

    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    @Builder.Default
    private AttributeDataType dataType = AttributeDataType.TEXT;

    private String unit;

    @Column(name = "is_filterable", nullable = false)
    private boolean filterable;

    @Column(name = "is_searchable", nullable = false)
    private boolean searchable;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

}
