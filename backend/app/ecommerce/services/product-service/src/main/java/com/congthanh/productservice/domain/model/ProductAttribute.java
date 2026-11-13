package com.congthanh.productservice.domain.model;

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
