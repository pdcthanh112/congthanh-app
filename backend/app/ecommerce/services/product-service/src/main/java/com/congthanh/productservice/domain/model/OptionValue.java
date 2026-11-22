package com.congthanh.productservice.domain.model;

/** Giá trị được phép của một trục variant trên sản phẩm gốc (Color = Black). */
public record OptionValue(long id, long optionId, String value, int displayOrder) {
}
