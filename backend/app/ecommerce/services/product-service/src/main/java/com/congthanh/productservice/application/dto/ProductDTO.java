package com.congthanh.productservice.application.dto;

import com.congthanh.productservice.domain.model.Specifications;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Sản phẩm đọc từ write model (back-office). Trang storefront đọc read model qua ProductDetailVm. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    private String id;

    private String parentId;

    private String name;

    private String slug;

    private String sku;

    private String gtin;

    private BigDecimal price;

    private String currency;

    private List<ProductCategoryDTO> category;

    private boolean hasVariant;

    private boolean featured;

    private String description;

    /** Đường dẫn ảnh đại diện. */
    private String thumbnail;

    private List<ProductImageDTO> image;

    private List<ProductAttributeValueDTO> attribute;

    /** Thông số tự do chỉ để hiển thị (ADR 0033); chỉ có ở API chi tiết, danh sách để null. */
    private List<Specifications.Group> specifications;

    private String supplier;

    /** Id thương hiệu ở catalog-service (chuỗi để client JS không mất chính xác). */
    private String brand;

    private ProductStatus status;

    /** Khoá lạc quan: gửi lại khi cập nhật để không ghi đè thay đổi của người khác. */
    private Long version;
}
