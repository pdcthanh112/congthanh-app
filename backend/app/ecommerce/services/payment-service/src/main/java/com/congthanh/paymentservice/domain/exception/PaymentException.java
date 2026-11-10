package com.congthanh.paymentservice.domain.exception;

public class PaymentException extends RuntimeException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, DECLINED, UNSUPPORTED, UNAVAILABLE }

    private final Kind kind;

    public PaymentException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static PaymentException invalid(String m) { return new PaymentException(Kind.INVALID, m); }
    public static PaymentException notFound(String m) { return new PaymentException(Kind.NOT_FOUND, m); }
    public static PaymentException conflict(String m) { return new PaymentException(Kind.CONFLICT, m); }
    public static PaymentException declined(String m) { return new PaymentException(Kind.DECLINED, m); }
    public static PaymentException unsupported(String m) { return new PaymentException(Kind.UNSUPPORTED, m); }
    public static PaymentException unavailable(String m) { return new PaymentException(Kind.UNAVAILABLE, m); }

    public Kind kind() {
        return kind;
    }
}
