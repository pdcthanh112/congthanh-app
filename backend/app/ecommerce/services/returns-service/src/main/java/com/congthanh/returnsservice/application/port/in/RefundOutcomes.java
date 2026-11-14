package com.congthanh.returnsservice.application.port.in;

/** Workflow hoàn tiền báo kết quả về (qua activity của returns-service). Idempotent. */
public interface RefundOutcomes {

    void refunded(String returnId, String paymentId);

    void refundFailed(String returnId, String reason);
}
