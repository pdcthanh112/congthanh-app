package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
interface ProductViewJpaRepository extends JpaRepository<ProductViewEntity, Long> {

    @Query("SELECT count(v) FROM ProductViewEntity v WHERE v.productId = ?1")
    long countByProduct(String productId);
}
