package com.congthanh.checkoutservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class CheckoutException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, REJECTED, UNAVAILABLE }

    private final Kind kind;
    private final String code;

    public CheckoutException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public static CheckoutException invalid(String message) {
        return new CheckoutException(Kind.INVALID, "INVALID_REQUEST", message);
    }

    public static CheckoutException notFound(String message) {
        return new CheckoutException(Kind.NOT_FOUND, "NOT_FOUND", message);
    }

    public static CheckoutException conflict(String code, String message) {
        return new CheckoutException(Kind.CONFLICT, code, message);
    }

    public static CheckoutException rejected(String code, String message) {
        return new CheckoutException(Kind.REJECTED, code, message);
    }

    public static CheckoutException unavailable(String message) {
        return new CheckoutException(Kind.UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", message);
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorKind errorKind() {
        return switch (kind) {
            case INVALID -> ErrorKind.INVALID;
            case NOT_FOUND -> ErrorKind.NOT_FOUND;
            case CONFLICT -> ErrorKind.CONFLICT;
            case REJECTED -> ErrorKind.REJECTED;
            case UNAVAILABLE -> ErrorKind.UNAVAILABLE;
        };
    }
}
