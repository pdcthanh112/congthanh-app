package com.congthanh.orderservice.application.port.out;

import com.congthanh.orderservice.domain.model.Money;

import java.util.Collection;
import java.util.Map;

/** Giá và trạng thái bán của sản phẩm (product-service). */
public interface ProductCatalog {

    Map<String, ProductSnapshot> lookup(Collection<String> productIds);

    record ProductSnapshot(String productId, String sku, String name, Money price, boolean purchasable,
                           int maxQuantityPerOrder) {
    }
}
