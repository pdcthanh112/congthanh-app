package com.congthanh.orderservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

/** Lỗi nghiệp vụ có phân loại, để adapter đổi sang HTTP/gRPC status hoặc Temporal failure. */
public class OrderException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT, FORBIDDEN, UNAVAILABLE, REJECTED }

    private final Kind kind;
    private final String code;

    public OrderException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public static OrderException invalid(String message) {
        return new OrderException(Kind.INVALID, "INVALID", message);
    }

    public static OrderException notFound(String message) {
        return new OrderException(Kind.NOT_FOUND, "NOT_FOUND", message);
    }

    public static OrderException conflict(String message) {
        return new OrderException(Kind.CONFLICT, "CONFLICT", message);
    }

    public static OrderException forbidden(String message) {
        return new OrderException(Kind.FORBIDDEN, "FORBIDDEN", message);
    }

    public static OrderException unavailable(String message) {
        return new OrderException(Kind.UNAVAILABLE, "UNAVAILABLE", message);
    }

    /** Checkout bị từ chối vì lý do nghiệp vụ (hết hàng, voucher hết lượt...): saga không retry. */
    public static OrderException rejected(String code, String message) {
        return new OrderException(Kind.REJECTED, code, message);
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
            case FORBIDDEN -> ErrorKind.FORBIDDEN;
            case UNAVAILABLE -> ErrorKind.UNAVAILABLE;
            case REJECTED -> ErrorKind.REJECTED;
        };
    }
}
