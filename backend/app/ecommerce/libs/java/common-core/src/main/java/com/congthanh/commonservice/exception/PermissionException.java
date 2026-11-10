package com.congthanh.commonservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class PermissionException extends RuntimeException{

    public PermissionException() {
        super();
    }

    public PermissionException(String message, Throwable cause) {
        super(message, cause);
    }

    public PermissionException(String message) {
        super(message);
    }

    public PermissionException(Throwable cause) {
        super(cause);
    }
}
