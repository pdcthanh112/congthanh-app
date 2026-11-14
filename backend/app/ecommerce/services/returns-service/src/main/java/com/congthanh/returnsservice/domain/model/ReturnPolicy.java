package com.congthanh.returnsservice.domain.model;

import com.congthanh.returnsservice.domain.exception.ReturnException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ReturnPolicy(Duration window, BigDecimal restockingFeePercent, boolean autoApprove) {

    public ReturnPolicy {
        if (window == null || window.isNegative() || window.isZero()) {
            throw ReturnException.invalid("return window must be positive");
        }
        restockingFeePercent = restockingFeePercent == null ? BigDecimal.ZERO : restockingFeePercent;
        if (restockingFeePercent.signum() < 0 || restockingFeePercent.compareTo(new BigDecimal("100")) > 0) {
            throw ReturnException.invalid("restocking fee percent must be within 0..100");
        }
    }

    public void checkEligible(OrderSnapshot order, List<ReturnLine> lines, Map<String, Integer> alreadyReturned,
                              Instant now) {
        if (!order.confirmed()) {
            throw ReturnException.notEligible("ORDER_NOT_CONFIRMED", "order " + order.orderId() + " is " + order.status());
        }
        if (order.deliveredAt() == null) {
            throw ReturnException.notEligible("NOT_DELIVERED", "order " + order.orderId() + " has not been delivered yet");
        }
        if (now.isAfter(order.deliveredAt().plus(window))) {
            throw ReturnException.notEligible("RETURN_WINDOW_CLOSED",
                    "return window of " + window.toDays() + " days closed for order " + order.orderId());
        }
        for (ReturnLine l : lines) {
            OrderSnapshot.Line ordered = order.line(l.productId()).orElseThrow(() -> ReturnException.notEligible(
                    "NOT_IN_ORDER", "product " + l.productId() + " is not in order " + order.orderId()));
            int left = ordered.quantity() - alreadyReturned.getOrDefault(l.productId(), 0);
            if (l.quantity() > left) {
                throw ReturnException.notEligible("QUANTITY_EXCEEDED",
                        "only " + left + " of product " + l.productId() + " can still be returned");
            }
        }
    }

    public BigDecimal restockingFee(ReturnReason reason, BigDecimal lineValue) {
        if (reason.sellerFault() || restockingFeePercent.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return lineValue.multiply(restockingFeePercent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
