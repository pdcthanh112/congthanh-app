package com.congthanh.productservice.domain.exception;


public class InvalidParameterException extends IllegalArgumentException {

    public InvalidParameterException() {
        super();
    }

    public InvalidParameterException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidParameterException(String message) {
        super(message);
    }

    public InvalidParameterException(Throwable cause) {
        super(cause);
    }

}
