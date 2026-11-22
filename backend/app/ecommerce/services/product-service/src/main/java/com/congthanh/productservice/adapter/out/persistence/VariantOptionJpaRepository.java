package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
interface VariantOptionJpaRepository extends JpaRepository<VariantOptionEntity, Long> {

    Optional<VariantOptionEntity> findByNameIgnoreCase(String name);
}
