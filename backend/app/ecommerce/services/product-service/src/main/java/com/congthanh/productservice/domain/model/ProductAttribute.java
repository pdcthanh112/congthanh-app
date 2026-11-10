package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@Table(name = "product_attribute")
public class ProductAttribute extends BaseEntity {

    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

//    @ManyToOne
//    @JoinColumn(name = "product_attribute_group")
//    private ProductAttributeGroup productAttributeGroup;
//
//    @OneToMany(mappedBy = "productAttribute")
//    @JsonIgnore
//    private List<ProductAttributeTemplate> productAttributeTemplates = new ArrayList<>();

    @OneToMany(mappedBy = "productAttribute")
    private List<ProductAttributeValue> attributeValues = new ArrayList<>();

}
