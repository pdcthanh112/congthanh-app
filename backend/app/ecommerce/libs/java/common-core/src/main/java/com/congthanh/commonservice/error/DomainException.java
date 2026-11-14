package com.congthanh.commonservice.error;

/**
 * Gốc của mọi lỗi nghiệp vụ. Thuần Java để domain kế thừa được; adapter (HTTP, gRPC) đọc
 * {@link #errorKind()} và {@link #code()} để đổi sang status tương ứng.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract ErrorKind errorKind();

    /** Mã máy đọc được, dạng UPPER_SNAKE (OUT_OF_STOCK, PRICE_CHANGED...). */
    public String code() {
        return errorKind().name();
    }
}
