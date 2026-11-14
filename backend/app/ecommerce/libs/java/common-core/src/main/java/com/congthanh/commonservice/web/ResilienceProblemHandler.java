package com.congthanh.commonservice.web;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Resilience4j từ chối cuộc gọi (circuit mở, bulkhead đầy, quá rate): trả 503/429 kèm Retry-After. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ResilienceProblemHandler {

    private final Problems problems;

    public ResilienceProblemHandler(Problems problems) {
        this.problems = problems;
    }

    @ExceptionHandler({CallNotPermittedException.class, BulkheadFullException.class})
    ResponseEntity<ProblemDetail> dependencyUnavailable(RuntimeException e) {
        ProblemDetail problem = problems.of(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "a dependency is temporarily unavailable, please retry");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "2").body(problem);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    ResponseEntity<ProblemDetail> rateLimited(RequestNotPermitted e) {
        ProblemDetail problem = problems.of(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "too many requests, please retry");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "1").body(problem);
    }
}
