package com.congthanh.productservice.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Thay toàn bộ mẫu thông số của một danh mục; thứ tự trong danh sách là thứ tự hiển thị nếu không ghi displayOrder. */
public record CategoryAttributeRequest(@NotNull @Size(max = 100) List<@Valid Item> attributes) {

    public record Item(@NotNull Long attributeId, boolean required, Integer displayOrder) {
    }
}
