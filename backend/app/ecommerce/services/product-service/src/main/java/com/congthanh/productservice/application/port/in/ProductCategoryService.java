package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.domain.model.ProductCategory;

import java.util.List;

public interface ProductCategoryService {

    void addCategoryToProduct(String categoryId, String productId);

    void addCategoryToProduct(List<String> categoryIds, String productId);
}
