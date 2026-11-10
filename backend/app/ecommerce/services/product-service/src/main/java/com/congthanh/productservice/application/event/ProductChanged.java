package com.congthanh.productservice.application.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sự kiện nội bộ (Spring ApplicationEvent) mỗi khi sản phẩm đổi. Hai adapter nghe nó:
 * <ul>
 *   <li>trước commit: ghi outbox (ProductUpserted/ProductDeleted) trong cùng transaction;</li>
 *   <li>sau commit: cập nhật read model MongoDB (CQRS, thay cho projection của Axon).</li>
 * </ul>
 */
public record ProductChanged(String productId, boolean deleted, String sku, String name, String slug,
                             List<String> categoryIds, BigDecimal price, String status, String description,
                             boolean hasVariant, boolean featured) {

    public static ProductChanged deleted(String productId) {
        return new ProductChanged(productId, true, null, null, null, List.of(), null, "DELETED", null, false, false);
    }
}
