package com.congthanh.productservice.application.port.in;

/** Phát lại ProductUpserted/ProductDeleted cho mọi sản phẩm qua outbox, để consumer (search-service...) dựng lại chỉ mục. */
public interface ProductEventReplayUseCase {

    /** Chạy lâu; mỗi sản phẩm một transaction nên lỗi một sản phẩm không chặn các sản phẩm khác. */
    Result replayAll();

    record Result(int published, int failed) {
    }
}
