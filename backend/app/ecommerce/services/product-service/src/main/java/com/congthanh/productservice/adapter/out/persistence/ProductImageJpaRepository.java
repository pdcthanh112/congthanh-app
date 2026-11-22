package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface ProductImageJpaRepository extends JpaRepository<ProductImageEntity, Long> {

    /** Ảnh mặc định trước, rồi theo thứ tự hiển thị. */
    @Query("SELECT i FROM ProductImageEntity i WHERE i.product.id = ?1 ORDER BY i.isDefault DESC, i.displayOrder, i.id")
    List<ProductImageEntity> findByProductOrdered(String productId);
}
