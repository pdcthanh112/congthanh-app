package com.congthanh.promotionservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "promotions")
public class PromotionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String code;

    @Column(nullable = false)
    String type;

    @Column(nullable = false)
    String discountKind;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal discountValue;

    @Column(nullable = false, length = 3)
    String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal minSubtotal;

    @Column(precision = 19, scale = 4)
    BigDecimal maxDiscount;

    int usageLimit;
    int perCustomerLimit;
    int usedCount;
    Instant startsAt;
    Instant endsAt;
    boolean active;

    @Column(columnDefinition = "text")
    String description;

    @Version
    long version;
}
