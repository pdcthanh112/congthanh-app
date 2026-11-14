package com.congthanh.storedvalueservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class StoredValueException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, FORBIDDEN, INSUFFICIENT_FUNDS }

    private final Kind kind;

    public StoredValueException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static StoredValueException invalid(String m) { return new StoredValueException(Kind.INVALID, m); }
    public static StoredValueException notFound(String m) { return new StoredValueException(Kind.NOT_FOUND, m); }
    public static StoredValueException conflict(String m) { return new StoredValueException(Kind.CONFLICT, m); }
    public static StoredValueException forbidden(String m) { return new StoredValueException(Kind.FORBIDDEN, m); }
    public static StoredValueException insufficient(String m) { return new StoredValueException(Kind.INSUFFICIENT_FUNDS, m); }

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
