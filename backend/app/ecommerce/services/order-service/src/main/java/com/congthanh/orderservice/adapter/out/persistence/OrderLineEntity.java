package com.congthanh.orderservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_lines")
public class OrderLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    OrderEntity order;

    @Column(nullable = false)
    String productId;

    String sku;

    String name;

    @Column(nullable = false)
    int quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal unitPrice;
}
