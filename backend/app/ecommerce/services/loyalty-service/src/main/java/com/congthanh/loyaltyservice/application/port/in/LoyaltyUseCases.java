package com.congthanh.loyaltyservice.application.port.in;

import com.congthanh.loyaltyservice.domain.model.Lot;
import com.congthanh.loyaltyservice.domain.model.Tier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface LoyaltyUseCases {

    Summary summary(String customerId);

    void orderConfirmed(String orderId, String customerId, BigDecimal totalUsd);

    void orderDelivered(String orderId, Instant at);

    void refunded(String orderId, BigDecimal amountUsd, String reason);

    void orderCancelled(String orderId);

    /** @return giá trị USD của số điểm được giữ */
    BigDecimal reserve(String redemptionId, String customerId, String orderId, long points);

    void commit(String redemptionId);

    void release(String redemptionId);

    /** Việc định kỳ: xác nhận lô qua hạn trả hàng, hết hạn lô, tính lại hạng. @return số thành viên đã xử lý */
    int housekeeping();

    record Summary(String customerId, Tier tier, long available, long pending, BigDecimal availableValueUsd,
                   List<Lot> lots) {
    }
}
