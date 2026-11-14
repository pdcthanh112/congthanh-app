package com.congthanh.returnsservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class ReturnException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, FORBIDDEN, NOT_ELIGIBLE }

    private final Kind kind;
    private final String code;

    public ReturnException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public static ReturnException invalid(String m) { return new ReturnException(Kind.INVALID, "INVALID", m); }
    public static ReturnException notFound(String m) { return new ReturnException(Kind.NOT_FOUND, "NOT_FOUND", m); }
    public static ReturnException conflict(String m) { return new ReturnException(Kind.CONFLICT, "CONFLICT", m); }
    public static ReturnException forbidden(String m) { return new ReturnException(Kind.FORBIDDEN, "FORBIDDEN", m); }

    public static ReturnException notEligible(String code, String m) { return new ReturnException(Kind.NOT_ELIGIBLE, code, m); }

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
            case NOT_ELIGIBLE -> ErrorKind.REJECTED;
        };
    }

    @Override
    public String code() {
        return code;
    }
}
