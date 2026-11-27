package com.congthanh.productservice.application.port.out;

import com.congthanh.productservice.domain.model.CategoryAttributeTemplate;

/** Lưu mẫu thông số theo danh mục (ADR 0033). */
public interface CategoryAttributeRepository {

    /** Mẫu của danh mục, item theo thứ tự hiển thị; danh mục chưa có mẫu thì items rỗng. */
    CategoryAttributeTemplate find(String categoryId);

    /** Thay toàn bộ mẫu của danh mục. */
    void replace(CategoryAttributeTemplate template);
}
