package com.congthanh.subscriptionservice.domain.model;

import com.congthanh.subscriptionservice.domain.exception.SubscriptionException;

public record Line(String productId, int quantity) {

    public Line {
        if (productId == null || productId.isBlank()) {
            throw SubscriptionException.invalid("productId is required");
        }
        if (quantity < 1 || quantity > 20) {
            throw SubscriptionException.invalid("quantity must be within 1..20");
        }
    }
}
