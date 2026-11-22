package com.congthanh.productservice.domain.model;

import java.math.BigDecimal;

/** Giá trị đã chuẩn hoá theo kiểu của thuộc tính; {@code number} chỉ có với thuộc tính NUMBER. */
public record AttributeValue(long attributeId, String value, BigDecimal number) {
}
