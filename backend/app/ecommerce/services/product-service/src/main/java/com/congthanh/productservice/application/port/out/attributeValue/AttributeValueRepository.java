package com.congthanh.productservice.application.port.out.attributeValue;

import com.congthanh.productservice.domain.model.ProductAttributeValue;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@Transactional
public interface AttributeValueRepository extends JpaRepository<ProductAttributeValue, Long>, AttributeValueCustomRepository {
}
