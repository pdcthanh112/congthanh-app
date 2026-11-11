package com.congthanh.checkoutservice.domain.exception;

public class CheckoutException extends RuntimeException {

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

    public String code() {
        return code;
    }
}
