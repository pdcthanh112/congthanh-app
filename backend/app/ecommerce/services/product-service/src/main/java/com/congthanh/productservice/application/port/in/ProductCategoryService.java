package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductCategoryDTO;

import java.util.List;

/** Gán/bỏ danh mục của sản phẩm; mỗi thay đổi phát ProductChanged để read model và chỉ mục tìm kiếm cập nhật. */
public interface ProductCategoryService {

    /** Danh mục phải tồn tại ở catalog-service; đã gán rồi thì 409. */
    List<ProductCategoryDTO> addCategoryToProduct(String productId, String categoryId);

    List<ProductCategoryDTO> removeCategoryFromProduct(String productId, String categoryId);
}
