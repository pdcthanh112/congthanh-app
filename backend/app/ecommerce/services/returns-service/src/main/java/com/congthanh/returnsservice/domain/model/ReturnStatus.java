package com.congthanh.returnsservice.domain.model;

public enum ReturnStatus {
    REQUESTED, APPROVED, REJECTED, RECEIVED, REFUND_PENDING, REFUNDED, REFUND_FAILED, CANCELLED;

    public boolean open() {
        return this != REJECTED && this != CANCELLED;
    }
}
