package com.congthanh.disputeservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Những gì service biết về đơn bị tranh chấp, dựng từ event order/payment/shipping/returns.
 *
 * @param refunded tổng đã hoàn cho đơn (payment + returns)
 */
public record OrderFacts(String orderId, String customerId, String recipientName, String shippingRegion,
                         String shippingPostalCode, List<String> productIds, BigDecimal total, String carrier,
                         String trackingNumber, Instant shippedAt, Instant deliveredAt, BigDecimal refunded,
                         Instant lastRefundAt) {

    public boolean delivered() {
        return deliveredAt != null;
    }
}
