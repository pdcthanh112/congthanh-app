package com.congthanh.checkoutservice.application.port.out;

import java.util.Collection;
import java.util.Map;

public interface Catalog {

    Map<String, Product> lookup(Collection<String> productIds);

    record Product(String productId, String sku, String name, boolean purchasable, int maxQuantityPerOrder,
                   int weightGrams, long taxClassId) {
    }
}
