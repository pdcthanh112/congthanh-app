package com.congthanh.productservice.application.port.out;

import java.util.List;

/** Danh mục sản phẩm (catalog-service, gRPC). */
public interface CategoryCatalog {

    CategoryRef byId(String categoryId);

    CategoryRef bySlug(String slug);

    List<CategoryRef> byIds(List<String> categoryIds);

    /** Ném IllegalArgumentException nếu có id không tồn tại. */
    void validate(List<String> categoryIds);

    record CategoryRef(String id, String name, String slug) {
    }
}
