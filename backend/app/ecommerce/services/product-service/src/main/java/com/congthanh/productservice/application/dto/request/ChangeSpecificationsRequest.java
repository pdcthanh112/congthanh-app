package com.congthanh.productservice.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Thay toàn bộ thông số tự do của sản phẩm (ADR 0033). {@code version} là version sản phẩm client đang có (khoá lạc
 * quan): người khác vừa sửa thì 409. Gửi danh sách rỗng để xoá hết.
 */
public record ChangeSpecificationsRequest(@NotNull Long version, @NotNull @Size(max = 30) List<@Valid Group> groups) {

    public record Group(@Size(max = 120) String name, @NotNull @Size(max = 300) List<@Valid Item> items) {
    }

    public record Item(@Size(max = 120) String name, @Size(max = 1000) String value) {
    }
}
