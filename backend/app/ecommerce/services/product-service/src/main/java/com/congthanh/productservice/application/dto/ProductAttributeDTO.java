package com.congthanh.productservice.application.dto;

import com.congthanh.productservice.domain.model.enums.AttributeDataType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** Định nghĩa thuộc tính mô tả. {@code code} bắt buộc với thuộc tính lọc được (khoá facet ở search-service). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    private Long id;

    private String code;

    private String name;

    private AttributeDataType dataType;

    private String unit;

    private boolean filterable;

    private boolean searchable;

    private int displayOrder;
}
