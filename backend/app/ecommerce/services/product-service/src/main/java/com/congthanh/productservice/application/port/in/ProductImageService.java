package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductImageDTO;

import java.util.List;

public interface ProductImageService {

    List<ProductImageDTO> getImageByProduct(String productId);

    ProductImageDTO addProductImage(ProductImageDTO productImageDTO);

}
