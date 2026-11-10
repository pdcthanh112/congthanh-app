package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.request.ProductAttributeValueRequest;

import java.util.List;

public interface ProductAttributeValueService {

    List<ProductAttributeValueDTO> getAttributeByProduct(String productId);

    ProductAttributeValueDTO createAttributeValue(ProductAttributeValueRequest productAttributeValueRequest);

    ProductAttributeValueDTO updateAttributeValue(ProductAttributeValueRequest productAttributeValueRequest);

}
