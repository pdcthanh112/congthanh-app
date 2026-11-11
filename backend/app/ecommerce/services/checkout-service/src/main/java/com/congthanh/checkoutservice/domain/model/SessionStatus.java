package com.congthanh.checkoutservice.domain.model;

/**
 * <pre>
 * OPEN ──place──► (fraud APPROVE) ──► PLACED (đơn đã tạo, saga Temporal chạy tiếp)
 *   │                 (fraud REVIEW) ──► PENDING_REVIEW ──duyệt APPROVE──► PLACED
 *   │                                         └──────────duyệt REJECT──► REJECTED
 *   │                 (fraud REJECT) ──► REJECTED
 *   ├──hết hạn──► EXPIRED
 *   └──huỷ──────► CANCELLED
 * </pre>
 */
public enum SessionStatus {
    OPEN, PENDING_REVIEW, PLACED, REJECTED, EXPIRED, CANCELLED;

    public boolean terminal() {
        return this == PLACED || this == REJECTED || this == EXPIRED || this == CANCELLED;
    }
}
