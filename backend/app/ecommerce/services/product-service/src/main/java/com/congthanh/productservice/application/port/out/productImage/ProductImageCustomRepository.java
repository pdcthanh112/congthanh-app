package com.congthanh.productservice.application.port.out.productImage;

import com.congthanh.productservice.domain.model.ProductImage;
import java.util.List;

public interface ProductImageCustomRepository {

    List<ProductImage> getProductImages(String productId);

    void deleteByProductId(String productId);

}
