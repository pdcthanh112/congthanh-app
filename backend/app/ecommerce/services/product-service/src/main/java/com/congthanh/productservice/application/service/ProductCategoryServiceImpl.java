package com.congthanh.productservice.application.service;

import com.congthanh.productservice.domain.exception.NotFoundException;
import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.ProductCategory;
import com.congthanh.productservice.application.port.out.productCategory.ProductCategoryRepository;
import com.congthanh.productservice.application.port.in.ProductCategoryService;
import com.congthanh.productservice.domain.support.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;

    private final CategoryCatalog categoryCatalog;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public void addCategoryToProduct(String categoryId, String productId) {
        CategoryCatalog.CategoryRef checkCategory = categoryCatalog.byId(categoryId);
        if (checkCategory == null) {
            throw new NotFoundException("Category not exist");
        }
        ProductCategory productCategory = productCategoryRepository.findProductCategoriesByCategoryIdAndProductId(categoryId, productId);
        if (productCategory != null) {
            throw new RuntimeException("Category already exits");
        }

        ProductCategory newProductCategory = ProductCategory.builder()
                .id(snowflakeIdGenerator.nextId())
                .categoryId(categoryId)
                .product(Product.builder().id(productId).build())
                .build();

        productCategoryRepository.save(newProductCategory);
    }

    @Override
    public void addCategoryToProduct(List<String> categoryIds, String productId) {
        for (int i = 0; i < categoryIds.size(); i++) {
            ProductCategory productCategory = productCategoryRepository.findProductCategoriesByCategoryIdAndProductId(categoryIds.get(i), productId);
            if (productCategory != null) {
                throw new RuntimeException("Category already exits");
            }

            ProductCategory newProductCategory = ProductCategory.builder()
                    .id(snowflakeIdGenerator.nextId())
                    .categoryId(categoryIds.get(i))
                    .product(Product.builder().id(productId).build())
                    .displayOrder(i)
                    .build();

            productCategoryRepository.save(newProductCategory);
        }
    }
}
