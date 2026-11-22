package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/** Số tiền không âm kèm currency ISO 4217 (ADR 0029: không có currency mặc định). */
public record Money(BigDecimal amount, String currency) {

    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    public Money {
        if (amount == null) {
            throw new BadRequestException("price amount is required");
        }
        if (amount.signum() < 0) {
            throw new BadRequestException("price must not be negative");
        }
        if (currency == null || !ISO_4217.matcher(currency).matches()) {
            throw new BadRequestException("currency must be an ISO 4217 code: " + currency);
        }
    }

    /** Có số tiền thì bắt buộc currency; không có số tiền thì không có giá. Currency được chuẩn hoá chữ hoa. */
    public static Money ofNullable(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("currency is required when price is set");
        }
        return new Money(amount, currency.strip().toUpperCase(Locale.ROOT));
    }
}
