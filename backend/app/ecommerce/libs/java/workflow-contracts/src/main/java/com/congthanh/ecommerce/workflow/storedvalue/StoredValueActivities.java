package com.congthanh.ecommerce.workflow.storedvalue;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;

/**
 * Activity của stored-value-service (task queue {@code stored-value}). Mọi method idempotent:
 * credit theo {@code reference}, hold/capture/release theo {@code holdId}.
 * Không đủ số dư hoặc tài khoản bị khoá: ApplicationFailure không retry, type {@link #REJECTED}.
 */
@ActivityInterface(namePrefix = "StoredValue.")
public interface StoredValueActivities {

    String REJECTED = "StoredValueRejected";

    @ActivityMethod
    Result credit(CreditRequest request);

    @ActivityMethod
    Result hold(HoldRequest request);

    @ActivityMethod
    void capture(String holdId);

    @ActivityMethod
    void release(String holdId, String reason);

    /** @param reason RETURN_REFUND, GOODWILL, ADJUSTMENT */
    record CreditRequest(String customerId, BigDecimal amount, String currency, String reason, String reference) {
    }

    record HoldRequest(String customerId, String holdId, String orderId, BigDecimal amount, String currency) {
    }

    record Result(String accountId, BigDecimal balanceAfter) {
    }
}
