package com.congthanh.productservice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntityIdentityTest {

    @Test
    void attributeAndItsValuesCanLiveInHashSets() {
        ProductAttribute color = new ProductAttribute();
        color.setId(1L);
        color.setName("color");
        ProductAttributeValue red = new ProductAttributeValue();
        red.setId(2L);
        red.setProductAttribute(color);
        red.setValue("red");
        color.setAttributeValues(List.of(red));

        Set<ProductAttributeValue> set = new HashSet<>();
        set.add(red); 

        assertThat(set).containsExactly(red);
        assertThat(red.toString()).isNotBlank();
        assertThat(color.toString()).isNotBlank();
    }
}
