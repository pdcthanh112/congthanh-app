package com.congthanh.productservice.application.event;

import java.math.BigDecimal;
import java.util.List;

public record ProductChanged(String productId, boolean deleted, String sku, String name, String slug,
                             List<String> categoryIds, BigDecimal price, String status, String description,
                             boolean hasVariant, boolean featured) {

    public static ProductChanged deleted(String productId) {
        return new ProductChanged(productId, true, null, null, null, List.of(), null, "DELETED", null, false, false);
    }
}
