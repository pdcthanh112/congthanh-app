package com.congthanh.commonservice.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Xung đột ghi ở tầng dữ liệu: client đọc lại rồi thử lại. Không lộ tên bảng hay ràng buộc ra ngoài. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class PersistenceProblemHandler {

    private final Problems problems;

    public PersistenceProblemHandler(Problems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentModification(OptimisticLockingFailureException e) {
        return problems.of(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "the resource was modified concurrently, please retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrityViolation(DataIntegrityViolationException e) {
        return problems.of(HttpStatus.CONFLICT, "CONFLICT", "the request conflicts with existing data");
    }
}
