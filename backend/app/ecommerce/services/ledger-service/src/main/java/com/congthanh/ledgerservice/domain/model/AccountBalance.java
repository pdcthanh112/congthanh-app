package com.congthanh.ledgerservice.domain.model;

import java.math.BigDecimal;

/** Tổng Nợ/Có của một tài khoản theo một tiền tệ; {@link #balance()} là số dư theo chiều bình thường. */
public record AccountBalance(AccountCode account, String currency, BigDecimal debits, BigDecimal credits) {

    public BigDecimal balance() {
        return account.type().normalSide() == Side.DEBIT ? debits.subtract(credits) : credits.subtract(debits);
    }
}
