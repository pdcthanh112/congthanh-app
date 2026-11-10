package com.congthanh.promotionservice.adapter.in.web;

import com.congthanh.promotionservice.domain.exception.PromotionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(PromotionException.class)
    ProblemDetail promotion(PromotionException e) {
        HttpStatus status = switch (e.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        p.setProperty("code", e.kind().name());
        return p;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrent(ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "promotion was modified concurrently, retry");
    }
}
