package com.congthanh.checkoutservice.domain.model;

import com.congthanh.checkoutservice.domain.exception.CheckoutException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Tiền: số thập phân + mã ISO 4217. Phép tính chỉ giữa cùng tiền tệ. */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(fractionDigits(currency), RoundingMode.HALF_EVEN);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money ofMinor(long minor, String currency) {
        return new Money(BigDecimal.valueOf(minor, fractionDigits(currency)), currency);
    }

    /** JPY/KRW/VND không có phần lẻ; USD, EUR... có 2 chữ số. */
    public static int fractionDigits(String currency) {
        return switch (currency) {
            case "VND", "JPY", "KRW" -> 0;
            default -> 2;
        };
    }

    public long minor() {
        return amount.movePointRight(fractionDigits(currency)).longValueExact();
    }

    public Money plus(Money o) {
        same(o);
        return new Money(amount.add(o.amount), currency);
    }

    public Money minus(Money o) {
        same(o);
        return new Money(amount.subtract(o.amount), currency);
    }

    public Money times(int n) {
        return new Money(amount.multiply(BigDecimal.valueOf(n)), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public Money min(Money o) {
        same(o);
        return amount.compareTo(o.amount) <= 0 ? this : o;
    }

    private void same(Money o) {
        if (!currency.equals(o.currency)) {
            throw CheckoutException.invalid("currency mismatch: " + currency + " vs " + o.currency);
        }
    }
}
