package com.congthanh.orderservice.adapter.out.persistence;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Bản ghi JPA, chỉ adapter persistence biết tới; domain {@code Order} không có annotation nào. */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    String id;

    @Column(nullable = false, unique = true)
    String orderCode;

    @Column(nullable = false)
    String customerId;

    @Column(nullable = false)
    String status;

    @Column(nullable = false, length = 3)
    String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal discount;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal tax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal total;

    String promotionCode;

    @Column(nullable = false)
    String paymentMethod;

    String paymentToken;
    String returnUrl;
    String clientIp;
    String reservationId;
    String paymentId;
    String cancelReason;
    String note;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "fullName", column = @Column(name = "ship_full_name")),
            @AttributeOverride(name = "phone", column = @Column(name = "ship_phone")),
            @AttributeOverride(name = "line1", column = @Column(name = "ship_line1")),
            @AttributeOverride(name = "line2", column = @Column(name = "ship_line2")),
            @AttributeOverride(name = "ward", column = @Column(name = "ship_ward")),
            @AttributeOverride(name = "district", column = @Column(name = "ship_district")),
            @AttributeOverride(name = "city", column = @Column(name = "ship_city")),
            @AttributeOverride(name = "country", column = @Column(name = "ship_country")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "ship_postal_code"))})
    AddressEmbeddable shipping;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "fullName", column = @Column(name = "bill_full_name")),
            @AttributeOverride(name = "phone", column = @Column(name = "bill_phone")),
            @AttributeOverride(name = "line1", column = @Column(name = "bill_line1")),
            @AttributeOverride(name = "line2", column = @Column(name = "bill_line2")),
            @AttributeOverride(name = "ward", column = @Column(name = "bill_ward")),
            @AttributeOverride(name = "district", column = @Column(name = "bill_district")),
            @AttributeOverride(name = "city", column = @Column(name = "bill_city")),
            @AttributeOverride(name = "country", column = @Column(name = "bill_country")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "bill_postal_code"))})
    AddressEmbeddable billing;

    @Column(nullable = false)
    Instant placedAt;

    @Column(nullable = false)
    Instant updatedAt;

    @Version
    long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    List<OrderLineEntity> lines = new ArrayList<>();
}
