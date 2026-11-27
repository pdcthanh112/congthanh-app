package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.Actor;

/** Quyền sửa sản phẩm, kiểm ở service chứ không chỉ dựa vào role ở gateway (ADR 0010). */
public interface ProductAccessUseCase {

    /**
     * ADMIN sửa được mọi sản phẩm; SELLER chỉ sửa sản phẩm mình sở hữu (variant theo owner của sản phẩm gốc).
     * Không có sản phẩm thì 404, không có quyền thì 403.
     */
    void requireModifiable(String productId, Actor actor);
}
