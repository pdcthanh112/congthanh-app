package com.congthanh.disputeservice.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Dựng dữ kiện về đơn từ event (để gom bằng chứng không cần gọi đồng bộ service khác). */
public interface FactsProjection {

    void orderPlaced(String orderId, String customerId, String recipientName, String region, String postalCode,
                     List<String> productIds, BigDecimal total);

    void paymentCaptured(String paymentId, String orderId, String customerId, String provider, String providerRef);

    void shipmentStatus(String orderId, String status, String carrier, String trackingNumber, Instant at);

    void refunded(String orderId, String refundKey, BigDecimal amount, Instant at);
}
