package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductAttributeDTO;

import java.util.List;

public interface ProductAttributeService {

    List<ProductAttributeDTO> getAllProductAttribute();

    ProductAttributeDTO createProductAttribute(ProductAttributeDTO productAttributeDTO);

    ProductAttributeDTO updateProductAttribute(Long id, ProductAttributeDTO productAttributeDTO);
}
