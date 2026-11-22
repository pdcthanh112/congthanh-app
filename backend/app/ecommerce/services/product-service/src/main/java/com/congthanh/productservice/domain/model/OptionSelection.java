package com.congthanh.productservice.domain.model;

/** Variant chọn giá trị {@code optionValueId} trên trục {@code optionId}. */
public record OptionSelection(long optionId, long optionValueId, int displayOrder) {
}
