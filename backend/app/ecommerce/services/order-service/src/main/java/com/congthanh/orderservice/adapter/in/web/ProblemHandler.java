package com.congthanh.orderservice.adapter.in.web;

import com.congthanh.orderservice.domain.exception.OrderException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/** Lỗi → RFC 9457 Problem Details; {@code code} là mã máy đọc được (OUT_OF_STOCK, PROMOTION_EXPIRED...). */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OrderException.class)
    ProblemDetail order(OrderException e) {
        HttpStatus status = switch (e.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        p.setType(URI.create("https://congthanhapp.com/problems/" + e.code().toLowerCase().replace('_', '-')));
        p.setProperty("code", e.code());
        return p;
    }

    @ExceptionHandler(CallNotPermittedException.class)
    ProblemDetail circuitOpen(CallNotPermittedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "a dependency is temporarily unavailable, please retry");
        p.setProperty("code", "DEPENDENCY_UNAVAILABLE");
        return p;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrent(ObjectOptimisticLockingFailureException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "the order was modified concurrently");
        p.setProperty("code", "CONCURRENT_MODIFICATION");
        return p;
    }
}
