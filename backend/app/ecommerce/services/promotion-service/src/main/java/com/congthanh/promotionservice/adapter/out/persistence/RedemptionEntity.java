package com.congthanh.promotionservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "redemptions")
public class RedemptionEntity {

    @Id
    String orderId;

    @Column(nullable = false)
    Long promotionId;

    @Column(nullable = false)
    String customerId;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal discount;

    @Column(nullable = false, length = 3)
    String currency;

    boolean cancelled;

    @Column(nullable = false)
    Instant redeemedAt;
}
