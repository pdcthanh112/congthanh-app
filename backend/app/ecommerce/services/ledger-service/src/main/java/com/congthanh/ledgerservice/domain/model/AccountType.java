package com.congthanh.ledgerservice.domain.model;

/**
 * Loại tài khoản suy ra từ tiền tố của mã (asset:..., liability:...). Số dư "bình thường" của Asset,
 * Expense và Contra-revenue nằm bên Nợ; Liability, Equity, Revenue nằm bên Có.
 */
public enum AccountType {
    ASSET("asset", Side.DEBIT),
    LIABILITY("liability", Side.CREDIT),
    EQUITY("equity", Side.CREDIT),
    REVENUE("revenue", Side.CREDIT),
    CONTRA_REVENUE("contra_revenue", Side.DEBIT),
    EXPENSE("expense", Side.DEBIT);

    private final String prefix;
    private final Side normalSide;

    AccountType(String prefix, Side normalSide) {
        this.prefix = prefix;
        this.normalSide = normalSide;
    }

    public String prefix() {
        return prefix;
    }

    public Side normalSide() {
        return normalSide;
    }
}
