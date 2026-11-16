package com.congthanh.checkoutservice.application.port.out;

import java.util.Collection;
import java.util.Map;

public interface Catalog {

    Map<String, Product> lookup(Collection<String> productIds);

    record Product(String productId, String sku, String name, boolean purchasable, int maxQuantityPerOrder,
                   int weightGrams, long taxClassId, String currency) {
        /** {@code currency}: ISO 4217 của giá sản phẩm do product-service trả; rỗng nếu sản phẩm chưa có giá. */
    }
}
