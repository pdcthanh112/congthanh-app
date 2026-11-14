package com.congthanh.disputeservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class DisputeException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, FORBIDDEN }

    private final Kind kind;

    public DisputeException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static DisputeException invalid(String m) { return new DisputeException(Kind.INVALID, m); }
    public static DisputeException notFound(String m) { return new DisputeException(Kind.NOT_FOUND, m); }
    public static DisputeException conflict(String m) { return new DisputeException(Kind.CONFLICT, m); }
    public static DisputeException forbidden(String m) { return new DisputeException(Kind.FORBIDDEN, m); }

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
