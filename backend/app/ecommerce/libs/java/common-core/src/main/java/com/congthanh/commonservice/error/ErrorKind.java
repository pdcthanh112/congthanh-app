package com.congthanh.commonservice.error;

public enum ErrorKind {
    INVALID(400, "INVALID_ARGUMENT"),
    UNAUTHENTICATED(401, "UNAUTHENTICATED"),
    PAYMENT_REQUIRED(402, "FAILED_PRECONDITION"),
    FORBIDDEN(403, "PERMISSION_DENIED"),
    NOT_FOUND(404, "NOT_FOUND"),
    CONFLICT(409, "ABORTED"),
    REJECTED(422, "FAILED_PRECONDITION"),
    RATE_LIMITED(429, "RESOURCE_EXHAUSTED"),
    INTERNAL(500, "INTERNAL"),
    UNAVAILABLE(503, "UNAVAILABLE");

    private final int httpStatus;
    private final String grpcCode;

    ErrorKind(int httpStatus, String grpcCode) {
        this.httpStatus = httpStatus;
        this.grpcCode = grpcCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String grpcCode() {
        return grpcCode;
    }
}
