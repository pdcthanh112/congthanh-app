package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface ProductAttributeJpaRepository extends JpaRepository<ProductAttributeEntity, Long> {

    Optional<ProductAttributeEntity> findByCode(String code);

    Optional<ProductAttributeEntity> findByNameIgnoreCase(String name);

    List<ProductAttributeEntity> findAllByOrderByDisplayOrderAscNameAsc();

    @Query("SELECT count(v) > 0 FROM ProductAttributeValueEntity v WHERE v.productAttribute.id = ?1")
    boolean hasValues(long attributeId);
}
