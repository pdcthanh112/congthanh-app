package com.congthanh.returnsservice.application.port.out;

import java.math.BigDecimal;

public interface RefundWorkflows {

    void start(RefundRequest request);

    record RefundRequest(String returnId, String orderId, String customerId, BigDecimal amount, String currency,
                         String reason, String method) {
    }
}
