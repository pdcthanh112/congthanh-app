package com.congthanh.commonservice.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class BadRequestException extends DomainException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorKind errorKind() {
        return ErrorKind.INVALID;
    }
}
