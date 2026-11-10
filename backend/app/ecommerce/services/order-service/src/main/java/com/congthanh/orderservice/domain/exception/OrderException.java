package com.congthanh.orderservice.domain.exception;

/** Lỗi nghiệp vụ có phân loại, để adapter đổi sang HTTP/gRPC status hoặc Temporal failure. */
public class OrderException extends RuntimeException {

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

    public String code() {
        return code;
    }
}
