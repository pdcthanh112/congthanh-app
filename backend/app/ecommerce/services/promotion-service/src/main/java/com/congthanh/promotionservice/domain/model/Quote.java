package com.congthanh.promotionservice.domain.model;

import java.math.BigDecimal;

/** @param reason khi valid=false: NOT_FOUND, INACTIVE, NOT_STARTED, EXPIRED, EXHAUSTED, ALREADY_USED, MIN_ORDER, CURRENCY */
public record Quote(boolean valid, BigDecimal discount, String currency, String reason, Long promotionId) {

    public static Quote rejected(String reason, String currency) {
        return new Quote(false, BigDecimal.ZERO, currency, reason, null);
    }
}
