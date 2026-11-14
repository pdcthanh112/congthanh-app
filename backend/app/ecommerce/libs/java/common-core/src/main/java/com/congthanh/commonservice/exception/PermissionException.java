package com.congthanh.commonservice.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class PermissionException extends DomainException {

    public PermissionException(String message) {
        super(message);
    }

    public PermissionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorKind errorKind() {
        return ErrorKind.FORBIDDEN;
    }
}
