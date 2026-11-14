package com.congthanh.loyaltyservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một lô điểm từ một đơn. Lô có hạn riêng nên tiêu điểm theo FIFO lô sắp hết hạn trước và đảo điểm đúng lô
 * khi trả hàng.
 *
 * @param confirmAt lúc lô thành khả dụng (giao hàng + hạn trả); null khi chưa biết ngày giao
 * @param remaining số điểm còn lại (đã trừ phần tiêu, đảo, giữ chỗ)
 */
public record Lot(String id, String customerId, String orderId, long points, long remaining, Status status,
                  BigDecimal orderTotal, Instant earnedAt, Instant confirmAt, Instant expiresAt) {

    public enum Status { PENDING, AVAILABLE, EXPIRED, REVERSED }

    public Lot withRemaining(long r) {
        Status s = r == 0 && status == Status.PENDING ? Status.REVERSED : status;
        return new Lot(id, customerId, orderId, points, r, s, orderTotal, earnedAt, confirmAt, expiresAt);
    }

    public Lot withStatus(Status s, Instant expires) {
        return new Lot(id, customerId, orderId, points, remaining, s, orderTotal, earnedAt, confirmAt, expires);
    }

    public Lot withConfirmAt(Instant at) {
        return new Lot(id, customerId, orderId, points, remaining, status, orderTotal, earnedAt, at, expiresAt);
    }
}
