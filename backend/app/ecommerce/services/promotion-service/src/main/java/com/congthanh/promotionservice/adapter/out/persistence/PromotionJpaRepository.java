package com.congthanh.promotionservice.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface PromotionJpaRepository extends JpaRepository<PromotionEntity, Long> {

    Optional<PromotionEntity> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromotionEntity p where p.code = :code")
    Optional<PromotionEntity> lockByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromotionEntity p where p.id = :id")
    Optional<PromotionEntity> lockById(Long id);
}
