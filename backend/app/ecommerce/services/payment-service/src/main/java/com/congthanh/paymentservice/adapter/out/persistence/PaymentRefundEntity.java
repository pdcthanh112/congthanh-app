package com.congthanh.paymentservice.adapter.out.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_refunds")
public class PaymentRefundEntity {

    @Id
    String refundId;

    @JdbcTypeCode(SqlTypes.BIGINT) 
    @Column(nullable = false)
    String paymentId;

    @Column(nullable = false)
    String orderId;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal amount;

    @Column(nullable = false, length = 3)
    String currency;

    String reason;

    @Column(nullable = false)
    Instant createdAt;
}
