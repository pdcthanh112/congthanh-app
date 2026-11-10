package com.congthanh.productservice.application.port.out.productAtrributeValue;

import com.congthanh.productservice.domain.model.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long>, ProductAttributeValueCustomRepository {
}
