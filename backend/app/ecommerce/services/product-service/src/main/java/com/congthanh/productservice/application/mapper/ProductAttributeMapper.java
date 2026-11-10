package com.congthanh.productservice.application.mapper;

import com.congthanh.productservice.application.dto.ProductAttributeDTO;
import com.congthanh.productservice.domain.model.ProductAttribute;
import jakarta.annotation.PostConstruct;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class ProductAttributeMapper {

    private static final ModelMapper modelMapper = new ModelMapper();

    @PostConstruct
    private void configureModelMapper() {

    }

    public static ProductAttribute mapProductAttributeDTOToEntity(ProductAttributeDTO productAttributeDTO) {
        return modelMapper.map(productAttributeDTO, ProductAttribute.class);
    }

    public static ProductAttributeDTO mapProductAttributeEntityToDTO(ProductAttribute productAttribute) {
        return modelMapper.map(productAttribute, ProductAttributeDTO.class);
    }
}
