package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.request.ProductAttributeValueRequest;

import java.util.List;

public interface ProductAttributeValueService {

    List<ProductAttributeValueDTO> getAttributeByProduct(String productId);

    /**
     * Thêm giá trị cho một thuộc tính (một hoặc nhiều với MULTI_SELECT); thuộc tính đã có giá trị trên sản phẩm thì báo
     * xung đột (dùng update). Trả mọi giá trị của thuộc tính đó sau khi lưu.
     */
    List<ProductAttributeValueDTO> createAttributeValue(ProductAttributeValueRequest request);

    /** Thay toàn bộ giá trị của thuộc tính trên sản phẩm; trả mọi giá trị sau khi lưu. */
    List<ProductAttributeValueDTO> updateAttributeValue(ProductAttributeValueRequest request);

    void deleteAttributeValue(String productId, Long attributeId);
}
