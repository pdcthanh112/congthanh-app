package com.congthanh.productservice.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product_prices")
public class ProductPrice {

    @Id
    private Long id;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "tax_rate")
    private Long taxRate;

    @Column(name = "start_time", columnDefinition = "TIMESTAMPTZ")
    private Instant startTime;

    @Column(name = "end_time", columnDefinition = "TIMESTAMPTZ")
    private Instant endTime;

    @Column(name = "created_at")
    private Instant createdAt;
}
