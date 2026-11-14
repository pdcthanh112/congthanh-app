package com.congthanh.ecommerce.workflow.loyalty;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;

/**
 * Activity đổi điểm của loyalty-service (task queue {@code loyalty}), dùng trong saga checkout như
 * Promotion redeem/cancel. Idempotent theo {@code redemptionId}. Không đủ điểm: ApplicationFailure không retry,
 * type {@link #REJECTED}.
 */
@ActivityInterface(namePrefix = "Loyalty.")
public interface LoyaltyActivities {

    String REJECTED = "LoyaltyRejected";

    /** Giữ điểm; trả về giá trị USD được giảm. */
    @ActivityMethod
    BigDecimal reserve(String redemptionId, String customerId, String orderId, long points);

    @ActivityMethod
    void commit(String redemptionId);

    @ActivityMethod
    void release(String redemptionId);
}
