package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface VariantOptionCombinationJpaRepository extends JpaRepository<VariantOptionCombinationEntity, Long> {

    @Query("""
            SELECT c FROM VariantOptionCombinationEntity c JOIN FETCH c.variantOption JOIN FETCH c.variantOptionValue
            WHERE c.variant.id = ?1 ORDER BY c.displayOrder, c.id""")
    List<VariantOptionCombinationEntity> findByVariant(String variantId);

    /** Mọi lựa chọn của mọi variant thuộc một sản phẩm gốc. */
    @Query("""
            SELECT c FROM VariantOptionCombinationEntity c JOIN FETCH c.variant JOIN FETCH c.variantOption JOIN FETCH c.variantOptionValue
            WHERE c.parentId = ?1 ORDER BY c.displayOrder, c.id""")
    List<VariantOptionCombinationEntity> findByParent(String parentId);
}
