package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductAttributeDTO;
import com.congthanh.productservice.application.dto.request.ProductAttributeValueRequest;

import java.util.List;

public interface ProductAttributeService {

    List<ProductAttributeDTO> getAllProductAttribute();

    ProductAttributeDTO createProductAttribute(ProductAttributeDTO productAttributeDTO);

    void addAttributeToProduct(List<ProductAttributeValueRequest> request, String productId);
}
