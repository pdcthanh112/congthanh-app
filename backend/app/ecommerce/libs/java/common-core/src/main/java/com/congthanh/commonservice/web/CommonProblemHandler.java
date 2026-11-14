package com.congthanh.commonservice.web;

import com.congthanh.commonservice.error.DomainException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Xử lý lỗi HTTP dùng chung, đứng sau mọi {@code @RestControllerAdvice} của service. Service chỉ cần
 * advice riêng cho lỗi đặc thù (đặt {@code @Order} nhỏ hơn); lỗi nghiệp vụ kế thừa
 * {@link DomainException} thì không cần viết handler.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommonProblemHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CommonProblemHandler.class);

    private final Problems problems;

    public CommonProblemHandler(Problems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException e) {
        ProblemDetail problem = problems.of(e);
        if (problem.getStatus() >= 500) {
            log.warn("domain error {}: {}", e.code(), e.getMessage());
        }
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException e) {
        ProblemDetail problem = problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "request validation failed");
        problem.setProperty(Problems.ERRORS, e.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException e) {
        return problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "access is denied");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthenticated(AuthenticationException e) {
        return problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "authentication is required");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("unhandled exception", e);
        return problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "an unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problems.of(status, "VALIDATION_FAILED", "request validation failed");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        problem.setProperty(Problems.ERRORS, errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    /** Lỗi của Spring MVC (405, 415, body sai định dạng...) cũng mang {@code code} như lỗi nghiệp vụ. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(Problems.CODE))) {
            HttpStatus resolved = HttpStatus.resolve(status.value());
            problem.setProperty(Problems.CODE, resolved != null ? resolved.name() : "HTTP_" + status.value());
        }
        return response;
    }
}
