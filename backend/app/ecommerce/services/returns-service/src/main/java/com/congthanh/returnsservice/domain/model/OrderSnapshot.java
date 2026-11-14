package com.congthanh.returnsservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record OrderSnapshot(String orderId, String customerId, String status, String paymentId, String currency,
                            BigDecimal total, List<Line> lines, Instant deliveredAt) {

    public record Line(String productId, int quantity, BigDecimal unitPrice) {
    }

    public boolean confirmed() {
        return "CONFIRMED".equals(status);
    }

    public Optional<Line> line(String productId) {
        return lines.stream().filter(l -> l.productId().equals(productId)).findFirst();
    }
}
