package com.congthanh.disputeservice.domain.model;

public enum DisputeStatus {
    NEEDS_RESPONSE, SUBMITTED, ACCEPTED, WON, LOST;

    public boolean closed() {
        return this == ACCEPTED || this == WON || this == LOST;
    }
}
