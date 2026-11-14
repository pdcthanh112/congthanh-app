package com.congthanh.ecommerce.workflow.payment;

import java.math.BigDecimal;

public record PartialRefundResult(String refundId, String paymentId, BigDecimal amount, String currency,
                                  boolean fullyRefunded) {
}
