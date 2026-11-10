package com.congthanh.orderservice.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {

    @EntityGraph(attributePaths = "lines")
    Optional<OrderEntity> findWithLinesById(String id);

    @EntityGraph(attributePaths = "lines")
    Optional<OrderEntity> findByOrderCode(String orderCode);

    @EntityGraph(attributePaths = "lines")
    List<OrderEntity> findByCustomerIdOrderByPlacedAtDesc(String customerId, Limit limit);

    @EntityGraph(attributePaths = "lines")
    List<OrderEntity> findByStatusAndPlacedAtBeforeOrderByPlacedAt(String status, Instant before, Limit limit);
}
