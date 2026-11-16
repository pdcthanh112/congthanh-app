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

    /**
     * Giữ điểm cho đơn tính bằng {@code currency}; trả về giá trị được giảm theo đúng đơn vị đó. Đơn khác đơn vị tiền
     * của chương trình điểm bị từ chối ({@link #REJECTED}).
     */
    @ActivityMethod
    BigDecimal reserve(String redemptionId, String customerId, String orderId, long points, String currency);

    @ActivityMethod
    void commit(String redemptionId);

    @ActivityMethod
    void release(String redemptionId);
}
