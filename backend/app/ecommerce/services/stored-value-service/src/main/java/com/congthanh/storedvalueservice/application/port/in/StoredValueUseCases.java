package com.congthanh.storedvalueservice.application.port.in;

import com.congthanh.storedvalueservice.domain.model.Account;
import com.congthanh.storedvalueservice.domain.model.Movement;

import java.math.BigDecimal;
import java.util.List;

public interface StoredValueUseCases {

    List<Account> accounts(String customerId);

    List<Movement> history(String accountId, int limit);

    /** Idempotent theo reference: gọi lại trả kết quả lần đầu, không cộng hai lần. */
    Movement credit(String customerId, BigDecimal amount, String currency, String reason, String reference);

    /** Idempotent theo holdId. */
    Movement hold(String customerId, String holdId, String orderId, BigDecimal amount, String currency);

    void capture(String holdId);

    void release(String holdId, String reason);

    void setFrozen(String accountId, boolean frozen);

    /** Trả lại các giữ chỗ quá hạn (đơn không bao giờ xác nhận). */
    int releaseExpired();
}
