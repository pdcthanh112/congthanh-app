package com.congthanh.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRefundJpaRepository extends JpaRepository<PaymentRefundEntity, String> {
}
