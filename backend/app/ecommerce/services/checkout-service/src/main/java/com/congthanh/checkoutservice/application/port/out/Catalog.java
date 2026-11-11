package com.congthanh.checkoutservice.application.port.out;

import java.util.Collection;
import java.util.Map;

/** Thông tin bán của sản phẩm (product-service): còn bán không, giới hạn số lượng, khối lượng, nhóm thuế. */
public interface Catalog {

    Map<String, Product> lookup(Collection<String> productIds);

    record Product(String productId, String sku, String name, boolean purchasable, int maxQuantityPerOrder,
                   int weightGrams, long taxClassId) {
    }
}
