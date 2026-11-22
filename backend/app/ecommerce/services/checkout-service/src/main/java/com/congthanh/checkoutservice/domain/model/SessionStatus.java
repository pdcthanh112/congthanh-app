package com.congthanh.checkoutservice.domain.model;

public enum SessionStatus {
    OPEN, PENDING_REVIEW, PLACED, REJECTED, EXPIRED, CANCELLED;

    public boolean terminal() {
        return this == PLACED || this == REJECTED || this == EXPIRED || this == CANCELLED;
    }
}
