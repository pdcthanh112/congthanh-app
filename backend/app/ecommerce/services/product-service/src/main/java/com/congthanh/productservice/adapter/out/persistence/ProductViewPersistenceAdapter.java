package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.productservice.application.port.out.ProductViews;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class ProductViewPersistenceAdapter implements ProductViews {

    private final ProductViewJpaRepository jpa;

    ProductViewPersistenceAdapter(ProductViewJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public long record(String productId, String customerId, Instant viewedAt) {
        return jpa.save(ProductViewEntity.builder().productId(productId).viewBy(customerId).viewedAt(viewedAt).build()).getId();
    }

    @Override
    public long count(String productId) {
        return jpa.countByProduct(productId);
    }
}
