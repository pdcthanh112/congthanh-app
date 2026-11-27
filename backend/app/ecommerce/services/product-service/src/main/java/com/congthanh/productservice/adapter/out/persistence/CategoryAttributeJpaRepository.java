package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface CategoryAttributeJpaRepository extends JpaRepository<CategoryAttributeEntity, CategoryAttributeEntity.Key> {

    List<CategoryAttributeEntity> findByCategoryIdOrderByDisplayOrderAscAttributeIdAsc(String categoryId);

    @Modifying
    @Query("DELETE FROM CategoryAttributeEntity c WHERE c.categoryId = ?1")
    void deleteByCategory(String categoryId);
}
