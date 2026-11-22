package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductImageDTO;

import java.util.List;

public interface ProductImageService {

    /** Ảnh mặc định trước, rồi theo thứ tự hiển thị. */
    List<ProductImageDTO> getImageByProduct(String productId);

    /** Gắn ảnh đã tải lên media-service ({@code id} = media id); {@code isDefault} thì thành ảnh đại diện. */
    ProductImageDTO addProductImage(ProductImageDTO image);

    /** Gỡ ảnh; gỡ ảnh đại diện thì ảnh đầu tiên còn lại thành ảnh đại diện. */
    void deleteProductImage(String productId, Long imageId);
}
