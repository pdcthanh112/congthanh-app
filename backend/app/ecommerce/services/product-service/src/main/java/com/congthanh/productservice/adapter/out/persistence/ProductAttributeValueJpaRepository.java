package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface ProductAttributeValueJpaRepository extends JpaRepository<ProductAttributeValueEntity, Long> {

    @Query("""
            SELECT v FROM ProductAttributeValueEntity v JOIN FETCH v.productAttribute a
            WHERE v.product.id = ?1 ORDER BY a.displayOrder, a.name""")
    List<ProductAttributeValueEntity> findByProduct(String productId);

    @Query("SELECT v FROM ProductAttributeValueEntity v WHERE v.product.id = ?1 AND v.productAttribute.id = ?2")
    Optional<ProductAttributeValueEntity> findOne(String productId, Long attributeId);
}
