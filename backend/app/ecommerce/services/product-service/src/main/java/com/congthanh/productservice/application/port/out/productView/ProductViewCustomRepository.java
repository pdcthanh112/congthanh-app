package com.congthanh.productservice.application.port.out.productView;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

@Repository
@Transactional
public interface ProductViewCustomRepository {

    Long getTotalViewOfProduct(String productId);

}
