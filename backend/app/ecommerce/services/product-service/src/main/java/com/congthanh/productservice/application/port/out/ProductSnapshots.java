package com.congthanh.productservice.application.port.out;

import java.util.Optional;

/** Đọc bản chụp sản phẩm cho read model. Gọi trong transaction REPEATABLE READ để mọi câu đọc cùng một snapshot. */
public interface ProductSnapshots {

    /** Đồng hồ của Postgres (micro giây), gọi đầu transaction để làm version của read model. */
    long snapshotMicros();

    Optional<ProductSnapshot> byId(String id);

    /** Sản phẩm chưa xoá mang slug này. */
    Optional<ProductSnapshot> bySlug(String slug);
}
