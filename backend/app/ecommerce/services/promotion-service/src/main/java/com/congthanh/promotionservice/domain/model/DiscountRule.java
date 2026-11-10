package com.congthanh.promotionservice.domain.model;

import com.congthanh.promotionservice.domain.exception.PromotionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * @param kind        PERCENTAGE: value là % (0-100], cap bởi maxDiscount; FIXED_AMOUNT: value là số tiền
 * @param minSubtotal đơn tối thiểu (0 = không yêu cầu)
 * @param maxDiscount trần giảm cho PERCENTAGE (null = không trần)
 */
public record DiscountRule(Kind kind, BigDecimal value, String currency, BigDecimal minSubtotal, BigDecimal maxDiscount) {

    public enum Kind { PERCENTAGE, FIXED_AMOUNT }

    public DiscountRule {
        if (kind == null || value == null || value.signum() <= 0) {
            throw PromotionException.invalid("discount kind and a positive value are required");
        }
        if (kind == Kind.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw PromotionException.invalid("percentage must be at most 100");
        }
        try {
            currency = Currency.getInstance(currency).getCurrencyCode();
        } catch (RuntimeException e) {
            throw PromotionException.invalid("unknown currency " + currency);
        }
        minSubtotal = minSubtotal == null ? BigDecimal.ZERO : minSubtotal;
    }

    /** Số tiền giảm cho subtotal, không vượt quá subtotal, làm tròn theo số lẻ của tiền tệ. */
    public BigDecimal discountFor(BigDecimal subtotal) {
        int digits = Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits());
        BigDecimal d = switch (kind) {
            case PERCENTAGE -> subtotal.multiply(value).divide(BigDecimal.valueOf(100), digits, RoundingMode.DOWN);
            case FIXED_AMOUNT -> value;
        };
        if (maxDiscount != null && d.compareTo(maxDiscount) > 0) {
            d = maxDiscount;
        }
        return d.min(subtotal).setScale(digits, RoundingMode.DOWN);
    }
}
