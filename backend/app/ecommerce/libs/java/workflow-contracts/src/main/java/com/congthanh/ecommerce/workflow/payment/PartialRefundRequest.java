package com.congthanh.ecommerce.workflow.payment;

import java.math.BigDecimal;

/**
 * Hoàn một phần số đã thu (trả hàng).
 *
 * @param refundId khoá idempotency, returns-service dùng return id; gọi lại cùng refundId không hoàn hai lần
 */
public record PartialRefundRequest(String orderId, String refundId, BigDecimal amount, String currency, String reason) {
}
