package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.readmodel.ProductDocument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ProductReadModelUseCase {

    ProductDocument getById(String id);

    ProductDocument getBySlug(String slug);

    void refresh(String productId);

    void onPriceChanged(String productId, String priceList, BigDecimal amount, String currency, Instant at);

    void onRatingChanged(String productId, double average, long count, List<Long> stars, Instant at);

    void onStockChanged(String productId, String warehouseId, int available, Instant at);

    void onCategoryChanged(String categoryId, String name, String slug, Instant at);

    void onBrandChanged(long brandId, String name, String slug, Instant at);

    RebuildResult rebuildAll();

    record RebuildResult(int refreshed, int failed) {
    }
}
