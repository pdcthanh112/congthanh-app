package com.congthanh.ledgerservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class LedgerException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, FORBIDDEN }

    private final Kind kind;

    public LedgerException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static LedgerException invalid(String m) { return new LedgerException(Kind.INVALID, m); }
    public static LedgerException notFound(String m) { return new LedgerException(Kind.NOT_FOUND, m); }
    public static LedgerException conflict(String m) { return new LedgerException(Kind.CONFLICT, m); }
    public static LedgerException forbidden(String m) { return new LedgerException(Kind.FORBIDDEN, m); }

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
        };
    }

    @Override
    public String code() {
        return kind.name();
    }
}
