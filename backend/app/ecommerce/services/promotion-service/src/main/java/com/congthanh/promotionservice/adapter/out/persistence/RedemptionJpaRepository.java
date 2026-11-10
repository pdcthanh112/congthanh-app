package com.congthanh.promotionservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface RedemptionJpaRepository extends JpaRepository<RedemptionEntity, String> {

    int countByPromotionIdAndCustomerIdAndCancelledFalse(Long promotionId, String customerId);
}
