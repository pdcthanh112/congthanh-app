package com.congthanh.productservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa nhóm hiển thị của thông số. */
public record AttributeGroupRequest(@NotBlank @Size(max = 255) String name, int displayOrder) {
}
