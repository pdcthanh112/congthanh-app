package com.congthanh.productservice.application.dto.viewmodel;

import com.congthanh.productservice.domain.model.enums.ProductStatus;
import lombok.Builder;
import lombok.Data;

/** Thông tin gọn của sản phẩm cho storefront (đọc từ read model). Đủ chi tiết thì dùng ProductDetailVm. */
@Data
@Builder
public class ProductVm {

    private String id;

    private String name;

    private String slug;

    private String description;

    private String thumbnail;

    private ProductStatus status;
}
