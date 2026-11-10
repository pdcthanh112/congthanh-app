package com.congthanh.productservice.application.port.out.productAttribute;

import com.congthanh.productservice.domain.model.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long>, ProductAttributeCustomRepository {
}
