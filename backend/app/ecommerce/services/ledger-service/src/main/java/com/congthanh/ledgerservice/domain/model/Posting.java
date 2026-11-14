package com.congthanh.ledgerservice.domain.model;

import com.congthanh.ledgerservice.domain.exception.LedgerException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** Một dòng của bút toán: ghi Nợ hoặc Có một tài khoản. Số tiền luôn dương, chiều do {@link Side}. */
public record Posting(AccountCode account, Side side, BigDecimal amount, String currency) {

    public Posting {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(side, "side");
        if (amount == null || amount.signum() <= 0) {
            throw LedgerException.invalid("posting amount must be positive");
        }
        if (amount.scale() > 4) {
            throw LedgerException.invalid("posting amount has more than 4 decimals: " + amount);
        }
        try {
            currency = Currency.getInstance(currency).getCurrencyCode();
        } catch (RuntimeException e) {
            throw LedgerException.invalid("unknown currency " + currency);
        }
    }

    public static Posting debit(String account, BigDecimal amount, String currency) {
        return new Posting(AccountCode.of(account), Side.DEBIT, amount, currency);
    }

    public static Posting credit(String account, BigDecimal amount, String currency) {
        return new Posting(AccountCode.of(account), Side.CREDIT, amount, currency);
    }
}
