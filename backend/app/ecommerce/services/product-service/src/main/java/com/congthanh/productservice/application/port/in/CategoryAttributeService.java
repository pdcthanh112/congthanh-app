package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.CategoryAttributeDto;
import com.congthanh.productservice.application.dto.request.CategoryAttributeRequest;

import java.util.List;

/** Mẫu thông số theo danh mục (ADR 0033): form của seller và bộ lọc theo danh mục đọc từ đây. */
public interface CategoryAttributeService {

    /** Mẫu của danh mục theo thứ tự hiển thị; danh mục chưa có mẫu thì rỗng. */
    List<CategoryAttributeDto> template(String categoryId);

    /** Thay toàn bộ mẫu; danh mục phải tồn tại ở catalog-service và mọi thuộc tính phải tồn tại. */
    List<CategoryAttributeDto> replace(String categoryId, CategoryAttributeRequest request);
}
