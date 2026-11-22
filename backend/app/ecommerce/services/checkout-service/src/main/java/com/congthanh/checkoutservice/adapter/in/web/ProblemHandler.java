package com.congthanh.checkoutservice.adapter.in.web;

import com.congthanh.commonservice.web.Problems;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
class ProblemHandler {

    private final Problems problems;

    ProblemHandler(Problems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(PlaceLimiter.Busy.class)
    ResponseEntity<ProblemDetail> busy(PlaceLimiter.Busy e) {
        ProblemDetail p = problems.of(HttpStatus.SERVICE_UNAVAILABLE, "CHECKOUT_BUSY", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "2").body(p);
    }
}
