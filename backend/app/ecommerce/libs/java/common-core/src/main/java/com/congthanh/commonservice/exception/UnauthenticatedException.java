package com.congthanh.commonservice.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class UnauthenticatedException extends DomainException {

    public UnauthenticatedException(String message) {
        super(message);
    }

    public UnauthenticatedException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorKind errorKind() {
        return ErrorKind.UNAUTHENTICATED;
    }
}
