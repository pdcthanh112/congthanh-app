package com.congthanh.commonservice.web;

import com.congthanh.commonservice.error.DomainException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.Locale;

/**
 * Dựng body lỗi RFC 9457 theo một khuôn cho mọi service: {@code type} là URI của mã lỗi,
 * {@code code} là mã máy đọc được, {@code detail} là thông điệp cho người dùng.
 */
public final class Problems {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";

    private final String typeBase;

    public Problems(String typeBase) {
        this.typeBase = typeBase.endsWith("/") ? typeBase : typeBase + "/";
    }

    public ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(typeBase + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty(CODE, code);
        return problem;
    }

    public ProblemDetail of(DomainException e) {
        return of(HttpStatusCode.valueOf(e.errorKind().httpStatus()), e.code(), e.getMessage());
    }
}
