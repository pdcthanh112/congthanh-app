package com.congthanh.orderservice.domain.model;

import com.congthanh.orderservice.domain.exception.OrderException;

/** Một dòng hàng, chụp lại tên và giá tại thời điểm đặt (không đổi khi catalog đổi giá). */
public record OrderLine(String productId, String sku, String name, int quantity, Money unitPrice) {

    public OrderLine {
        if (productId == null || productId.isBlank()) {
            throw OrderException.invalid("productId is required");
        }
        if (quantity <= 0) {
            throw OrderException.invalid("quantity must be positive for product " + productId);
        }
        if (unitPrice == null || unitPrice.isNegative()) {
            throw OrderException.invalid("unit price must not be negative for product " + productId);
        }
    }

    public Money total() {
        return unitPrice.times(quantity);
    }
}
