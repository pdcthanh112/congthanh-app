package com.congthanh.productservice.application.port.out;

import com.congthanh.productservice.application.readmodel.ProductDocument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductReadModelStore {

    Optional<ProductDocument> findById(String id);

    Optional<ProductDocument> findBySlug(String slug);

    boolean upsertCore(ProductDocument core);

    void delete(String id);

    void applyPrice(String productId, String priceList, BigDecimal amount, String currency, Instant at);

    void applyRating(String productId, double average, long count, List<Long> stars, Instant at);

    void applyStock(String productId, String warehouseId, int available, Instant at);

    void applyCategory(String categoryId, String name, String slug, Instant at);

    void applyBrand(long brandId, String name, String slug, Instant at);
}
