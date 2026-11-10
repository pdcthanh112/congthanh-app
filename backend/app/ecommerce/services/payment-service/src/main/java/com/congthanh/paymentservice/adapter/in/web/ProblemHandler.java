package com.congthanh.paymentservice.adapter.in.web;

import com.congthanh.paymentservice.domain.exception.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    ProblemDetail payment(PaymentException e) {
        HttpStatus status = switch (e.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case DECLINED -> HttpStatus.PAYMENT_REQUIRED;
            case UNSUPPORTED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        p.setProperty("code", e.kind().name());
        return p;
    }
}
