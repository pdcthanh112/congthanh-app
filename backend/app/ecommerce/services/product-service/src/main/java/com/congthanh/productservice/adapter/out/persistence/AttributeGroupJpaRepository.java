package com.congthanh.productservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface AttributeGroupJpaRepository extends JpaRepository<AttributeGroupEntity, Long> {

    Optional<AttributeGroupEntity> findByNameIgnoreCase(String name);

    List<AttributeGroupEntity> findAllByOrderByDisplayOrderAscNameAsc();
}
