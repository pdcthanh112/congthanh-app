package com.congthanh.loyaltyservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class LoyaltyException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, FORBIDDEN, INSUFFICIENT_FUNDS }

    private final Kind kind;

    public LoyaltyException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static LoyaltyException invalid(String m) { return new LoyaltyException(Kind.INVALID, m); }
    public static LoyaltyException notFound(String m) { return new LoyaltyException(Kind.NOT_FOUND, m); }
    public static LoyaltyException conflict(String m) { return new LoyaltyException(Kind.CONFLICT, m); }
    public static LoyaltyException forbidden(String m) { return new LoyaltyException(Kind.FORBIDDEN, m); }
    public static LoyaltyException insufficient(String m) { return new LoyaltyException(Kind.INSUFFICIENT_FUNDS, m); }

    public Kind kind() {
        return kind;
    }

    @Override
    public ErrorKind errorKind() {
        return switch (kind) {
            case INVALID -> ErrorKind.INVALID;
            case NOT_FOUND -> ErrorKind.NOT_FOUND;
            case CONFLICT -> ErrorKind.CONFLICT;
            case FORBIDDEN -> ErrorKind.FORBIDDEN;
            case INSUFFICIENT_FUNDS -> ErrorKind.REJECTED;
        };
    }

    @Override
    public String code() {
        return kind.name();
    }
}
