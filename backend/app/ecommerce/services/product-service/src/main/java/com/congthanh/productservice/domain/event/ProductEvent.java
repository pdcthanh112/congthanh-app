package com.congthanh.productservice.domain.event;

/**
 * Event miền của aggregate Product. Adapter persistence đổi thành bản chụp protobuf (ProductUpserted/ProductDeleted)
 * và ghi outbox cùng transaction; đổi variant thì sản phẩm gốc cũng được phát lại (ADR 0030).
 */
public sealed interface ProductEvent {

    String productId();

    /** Có thay đổi bất kỳ trên sản phẩm (thông tin, giá, trạng thái, danh mục, ảnh, thuộc tính, option). */
    record ProductChanged(String productId) implements ProductEvent {
    }

    record ProductDeleted(String productId) implements ProductEvent {
    }
}
