package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.request.ProductAttributeValueRequest;

import java.util.List;

public interface ProductAttributeValueService {

    List<ProductAttributeValueDTO> getAttributeByProduct(String productId);

    /** Thêm mới; thuộc tính đã có giá trị trên sản phẩm thì báo xung đột (dùng update). */
    ProductAttributeValueDTO createAttributeValue(ProductAttributeValueRequest request);

    ProductAttributeValueDTO updateAttributeValue(ProductAttributeValueRequest request);

    void deleteAttributeValue(String productId, Long attributeId);
}
