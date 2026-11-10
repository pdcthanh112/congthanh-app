package com.congthanh.productservice.application.port.out.productImage;

import com.congthanh.productservice.domain.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long>, ProductImageCustomRepository {
}
