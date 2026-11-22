package com.congthanh.productservice.application.port.out;

import com.congthanh.productservice.domain.model.ProductAttribute;

import java.util.List;
import java.util.Optional;

public interface ProductAttributeRepository {

    Optional<ProductAttribute> findById(long id);

    Optional<ProductAttribute> findByCode(String code);

    Optional<ProductAttribute> findByNameIgnoreCase(String name);

    /** Theo thứ tự hiển thị rồi tên. */
    List<ProductAttribute> findAll();

    /** Đã có sản phẩm nào mang giá trị của thuộc tính này chưa. */
    boolean hasValues(long id);

    void save(ProductAttribute attribute);
}
