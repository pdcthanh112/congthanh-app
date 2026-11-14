package com.congthanh.ledgerservice.domain.model;

import com.congthanh.ledgerservice.domain.exception.LedgerException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mã tài khoản dạng {@code <loại>:<nhóm>[:<chi tiết>...]}, ví dụ {@code asset:psp_clearing:stripe},
 * {@code liability:sales_tax_payable:NY}. Chữ thường, số, gạch dưới; phần chi tiết cho phép chữ hoa (mã bang).
 */
public record AccountCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z_]+(:[A-Za-z0-9_\\-]+){1,4}$");

    public AccountCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw LedgerException.invalid("invalid account code " + value);
        }
        type(value);
    }

    public static AccountCode of(String value) {
        return new AccountCode(value);
    }

    public AccountType type() {
        return type(value);
    }

    private static AccountType type(String value) {
        String prefix = value.substring(0, value.indexOf(':')).toLowerCase(Locale.ROOT);
        for (AccountType t : AccountType.values()) {
            if (t.prefix().equals(prefix)) {
                return t;
            }
        }
        throw LedgerException.invalid("unknown account type in " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
