package com.congthanh.productservice.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeValueDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    private Long id;

    /** Tên thuộc tính (giữ tên trường cũ cho client hiện có). */
    private String attribute;

    private Long attributeId;

    private String attributeCode;

    private String product;

    private String value;

    private BigDecimal valueNumber;

    private String unit;
}
