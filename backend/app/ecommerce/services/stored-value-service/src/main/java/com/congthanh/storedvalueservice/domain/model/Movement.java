package com.congthanh.storedvalueservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một dòng lịch sử của tài khoản (chỉ ghi thêm), cũng là nguồn của event.
 *
 * @param reference khoá idempotency: id nghiệp vụ với CREDIT, hold id với HOLD/DEBIT/RELEASE
 */
public record Movement(String accountId, String customerId, Account.Kind kind, Type type, BigDecimal amount,
                       String currency, String reason, String reference, String orderId, BigDecimal balanceAfter,
                       Instant at) {

    public enum Type { CREDIT, HOLD, DEBIT, RELEASE }
}
