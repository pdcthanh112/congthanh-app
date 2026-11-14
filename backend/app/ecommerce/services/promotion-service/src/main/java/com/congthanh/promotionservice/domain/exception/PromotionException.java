package com.congthanh.promotionservice.domain.exception;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;

public class PromotionException extends DomainException {

    public enum Kind { INVALID, NOT_FOUND, CONFLICT }

    private final Kind kind;

    public PromotionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static PromotionException invalid(String m) { return new PromotionException(Kind.INVALID, m); }
    public static PromotionException notFound(String m) { return new PromotionException(Kind.NOT_FOUND, m); }
    public static PromotionException conflict(String m) { return new PromotionException(Kind.CONFLICT, m); }

    public Kind kind() {
        return kind;
    }

    @Override
    public ErrorKind errorKind() {
        return switch (kind) {
            case INVALID -> ErrorKind.INVALID;
            case NOT_FOUND -> ErrorKind.NOT_FOUND;
            case CONFLICT -> ErrorKind.CONFLICT;
        };
    }

    @Override
    public String code() {
        return kind.name();
    }
}
