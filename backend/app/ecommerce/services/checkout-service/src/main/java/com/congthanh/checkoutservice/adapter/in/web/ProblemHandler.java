package com.congthanh.checkoutservice.adapter.in.web;

import com.congthanh.checkoutservice.domain.exception.CheckoutException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/** Lỗi → RFC 9457 Problem Details; {@code code} là mã máy đọc được (PRICE_CHANGED, FRAUD_REJECTED...). */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CheckoutException.class)
    ProblemDetail checkout(CheckoutException e) {
        HttpStatus status = switch (e.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        p.setType(URI.create("https://congthanhapp.com/problems/" + e.code().toLowerCase().replace('_', '-')));
        p.setProperty("code", e.code());
        return p;
    }

    /** Pod đang xử lý đủ số lượt đặt đơn đồng thời: client đợi rồi gửi lại (cùng Idempotency-Key). */
    @ExceptionHandler(PlaceLimiter.Busy.class)
    ResponseEntity<ProblemDetail> busy(PlaceLimiter.Busy e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setProperty("code", "CHECKOUT_BUSY");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "2").body(p);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    ProblemDetail circuitOpen(CallNotPermittedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "a dependency is temporarily unavailable, please retry");
        p.setProperty("code", "DEPENDENCY_UNAVAILABLE");
        return p;
    }
}
