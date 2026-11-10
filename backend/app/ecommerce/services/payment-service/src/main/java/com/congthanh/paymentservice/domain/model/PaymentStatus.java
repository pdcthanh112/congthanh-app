package com.congthanh.paymentservice.domain.model;

/**
 * INITIATED ─► AUTHORIZED ─► CAPTURED ─► REFUNDED
 *     │            └────────► VOIDED
 *     ├─► PENDING_ACTION (khách đang ở cổng thanh toán) ─► AUTHORIZED | CAPTURED | DECLINED | VOIDED
 *     └─► DECLINED
 */
public enum PaymentStatus {
    INITIATED, PENDING_ACTION, AUTHORIZED, CAPTURED, DECLINED, VOIDED, REFUNDED;

    public boolean isTerminal() {
        return this == DECLINED || this == VOIDED || this == REFUNDED;
    }
}
