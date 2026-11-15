package com.congthanh.productservice.domain.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product_view")
public class ProductView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BIGINT) 
    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "view_by", nullable = false)
    private String viewBy;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

}
