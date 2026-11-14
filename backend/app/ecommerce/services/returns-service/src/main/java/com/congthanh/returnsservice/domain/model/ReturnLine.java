package com.congthanh.returnsservice.domain.model;

import com.congthanh.returnsservice.domain.exception.ReturnException;

import java.math.BigDecimal;
import java.util.Objects;

public record ReturnLine(String productId, int quantity, ReturnReason reason, BigDecimal unitPrice,
                         ItemCondition condition) {

    public ReturnLine {
        if (productId == null || productId.isBlank()) {
            throw ReturnException.invalid("productId is required");
        }
        if (quantity <= 0) {
            throw ReturnException.invalid("quantity must be positive");
        }
        Objects.requireNonNull(reason, "reason");
    }

    public static ReturnLine requested(String productId, int quantity, ReturnReason reason) {
        return new ReturnLine(productId, quantity, reason, null, null);
    }

    public ReturnLine priced(BigDecimal price) {
        return new ReturnLine(productId, quantity, reason, price, condition);
    }

    public ReturnLine inspected(ItemCondition c) {
        return new ReturnLine(productId, quantity, reason, unitPrice, c);
    }

    public BigDecimal value() {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
