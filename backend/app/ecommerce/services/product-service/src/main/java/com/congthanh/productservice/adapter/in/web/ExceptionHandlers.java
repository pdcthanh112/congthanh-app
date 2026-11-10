package com.congthanh.productservice.adapter.in.web;

import com.congthanh.productservice.application.dto.constant.ResponseStatus;
import com.congthanh.productservice.domain.exception.BadRequestException;
import com.congthanh.productservice.application.dto.response.Response;
import com.congthanh.productservice.domain.exception.InvalidParameterException;
import com.congthanh.productservice.domain.exception.NotFoundException;
import com.congthanh.productservice.domain.exception.PermissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionHandlers {

    @ExceptionHandler(value = NotFoundException.class)
    public ResponseEntity<Object> notFoundException(NotFoundException exception) {
        return failed(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(value = PermissionException.class)
    public ResponseEntity<Object> permissionException(PermissionException exception) {
        return failed(HttpStatus.FORBIDDEN, exception);
    }

    @ExceptionHandler(value = {BadRequestException.class, InvalidParameterException.class})
    public ResponseEntity<Object> badRequestException(RuntimeException exception) {
        return failed(HttpStatus.BAD_REQUEST, exception);
    }

    private static ResponseEntity<Object> failed(HttpStatus status, RuntimeException exception) {
        Response response = new Response();
        response.setMessage(exception.getMessage());
        response.setStatus(ResponseStatus.STATUS_FAILED);
        return ResponseEntity.status(status).body(response);
    }
}
