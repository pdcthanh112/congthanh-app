package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface VariantOptionValueJpaRepository extends JpaRepository<VariantOptionValueEntity, Long> {

    @Query("""
            SELECT v FROM VariantOptionValueEntity v JOIN FETCH v.variantOption o
            WHERE v.product.id = ?1 ORDER BY o.name, v.displayOrder, v.id""")
    List<VariantOptionValueEntity> findByParent(String parentId);

    @Query("""
            SELECT v FROM VariantOptionValueEntity v
            WHERE v.product.id = ?1 AND v.variantOption.id = ?2 AND lower(v.value) = lower(?3)""")
    Optional<VariantOptionValueEntity> findOne(String parentId, Long optionId, String value);
}
