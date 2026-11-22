package com.congthanh.productservice.application.port.out;

import java.time.Instant;

/** Lượt xem sản phẩm (chỉ ghi thêm). */
public interface ProductViews {

    /** @return id lượt xem */
    long record(String productId, String customerId, Instant viewedAt);

    long count(String productId);
}
