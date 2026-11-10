package com.congthanh.promotionservice.domain.exception;

public class PromotionException extends RuntimeException {

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
}
