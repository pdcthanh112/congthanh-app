package com.congthanh.storedvalueservice.domain.model;

import com.congthanh.storedvalueservice.domain.exception.StoredValueException;

import java.math.BigDecimal;
import java.time.Instant;

/** Phần tiền giữ cho một đơn. Chuyển trạng thái idempotent vì activity Temporal có thể gọi lại. */
public record Hold(String id, String accountId, String orderId, BigDecimal amount, Status status, Instant expiresAt) {

    public enum Status { HELD, CAPTURED, RELEASED }

    public boolean expired(Instant now) {
        return status == Status.HELD && now.isAfter(expiresAt);
    }

    public Hold captured() {
        if (status == Status.RELEASED) {
            throw StoredValueException.conflict("hold " + id + " was already released");
        }
        return new Hold(id, accountId, orderId, amount, Status.CAPTURED, expiresAt);
    }

    public Hold released() {
        if (status == Status.CAPTURED) {
            throw StoredValueException.conflict("hold " + id + " was already captured");
        }
        return new Hold(id, accountId, orderId, amount, Status.RELEASED, expiresAt);
    }
}
