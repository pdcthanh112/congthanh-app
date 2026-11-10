package com.congthanh.paymentservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    String id;

    @Column(nullable = false, unique = true)
    String orderId;

    String customerId;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal amount;

    @Column(nullable = false, length = 3)
    String currency;

    @Column(nullable = false)
    String method;

    @Column(nullable = false)
    String provider;

    String providerRef;

    @Column(nullable = false)
    String status;

    String redirectUrl;
    String failureReason;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    @Version
    long version;
}
