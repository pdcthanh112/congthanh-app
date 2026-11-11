package com.congthanh.paymentservice.domain.model;

import com.congthanh.paymentservice.domain.exception.PaymentException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw PaymentException.invalid("amount must be positive");
        }
        try {
            currency = Currency.getInstance(currency.toUpperCase()).getCurrencyCode();
        } catch (RuntimeException e) {
            throw PaymentException.invalid("unknown currency " + currency);
        }
    }

    /** Số tiền ở đơn vị nhỏ nhất của tiền tệ (USD: cent, JPY: yen) như các cổng thanh toán yêu cầu. */
    public long minorUnits() {
        int digits = Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits());
        return amount.setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).longValueExact();
    }
}
