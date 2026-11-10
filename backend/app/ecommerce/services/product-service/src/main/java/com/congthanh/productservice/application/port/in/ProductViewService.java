package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductViewDTO;

public interface ProductViewService {

    Long getTotalViewOfProduct(String productId);

    ProductViewDTO addProductView(String productId, String customerId);

}
