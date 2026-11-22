package com.congthanh.productservice.domain.model;

/** Sản phẩm thuộc một danh mục của catalog-service, theo thứ tự hiển thị (danh mục đầu tiên là danh mục chính). */
public record CategoryAssignment(String categoryId, int displayOrder) {
}
