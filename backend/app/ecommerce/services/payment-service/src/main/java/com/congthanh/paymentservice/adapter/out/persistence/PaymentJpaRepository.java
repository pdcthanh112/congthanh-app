package com.congthanh.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {

    Optional<PaymentEntity> findByOrderId(String orderId);

    Optional<PaymentEntity> findByProviderAndProviderRef(String provider, String providerRef);
}
