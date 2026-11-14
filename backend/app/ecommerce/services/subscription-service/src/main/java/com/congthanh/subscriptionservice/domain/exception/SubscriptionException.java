package com.congthanh.subscriptionservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class SubscriptionException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, FORBIDDEN, INSUFFICIENT_FUNDS }

    private final Kind kind;

    public SubscriptionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static SubscriptionException invalid(String m) { return new SubscriptionException(Kind.INVALID, m); }
    public static SubscriptionException notFound(String m) { return new SubscriptionException(Kind.NOT_FOUND, m); }
    public static SubscriptionException conflict(String m) { return new SubscriptionException(Kind.CONFLICT, m); }
    public static SubscriptionException forbidden(String m) { return new SubscriptionException(Kind.FORBIDDEN, m); }
    public static SubscriptionException insufficient(String m) { return new SubscriptionException(Kind.INSUFFICIENT_FUNDS, m); }

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
