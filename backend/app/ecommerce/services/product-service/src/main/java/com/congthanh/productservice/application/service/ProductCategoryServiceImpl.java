package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.productservice.application.dto.ProductCategoryDTO;
import com.congthanh.productservice.application.port.in.ProductCategoryService;
import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.port.out.ProductQueries;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.domain.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductRepository products;
    private final ProductQueries queries;
    private final CategoryCatalog categoryCatalog;

    @Override
    @Transactional
    public List<ProductCategoryDTO> addCategoryToProduct(String productId, String categoryId) {
        Product product = requireProduct(productId);
        try {
            categoryCatalog.validate(List.of(categoryId));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        product.assignCategory(categoryId);
        products.save(product);
        return categories(productId);
    }

    @Override
    @Transactional
    public List<ProductCategoryDTO> removeCategoryFromProduct(String productId, String categoryId) {
        Product product = requireProduct(productId);
        product.removeCategory(categoryId);
        products.save(product);
        return categories(productId);
    }

    private List<ProductCategoryDTO> categories(String productId) {
        return queries.detail(productId).map(d -> d.getCategory()).orElse(List.of());
    }

    private Product requireProduct(String id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("product not found: " + id));
    }
}
