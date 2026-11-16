package com.congthanh.loyaltyservice.application.port.in;

import com.congthanh.loyaltyservice.domain.model.Lot;
import com.congthanh.loyaltyservice.domain.model.Tier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface LoyaltyUseCases {

    Summary summary(String customerId);

    /** {@code currency}: đơn vị tiền của đơn. Đơn khác đơn vị tiền của chương trình điểm thì không tích điểm. */
    void orderConfirmed(String orderId, String customerId, BigDecimal total, String currency);

    void orderDelivered(String orderId, Instant at);

    void refunded(String orderId, BigDecimal amount, String currency, String reason);

    void orderCancelled(String orderId);

    /**
     * @param currency đơn vị tiền của đơn; phải trùng đơn vị tiền của chương trình điểm, nếu không sẽ bị từ chối
     * @return giá trị (theo {@code currency}) của số điểm được giữ
     */
    BigDecimal reserve(String redemptionId, String customerId, String orderId, long points, String currency);

    void commit(String redemptionId);

    void release(String redemptionId);

    /** Việc định kỳ: xác nhận lô qua hạn trả hàng, hết hạn lô, tính lại hạng. @return số thành viên đã xử lý */
    int housekeeping();

    record Summary(String customerId, Tier tier, long available, long pending, BigDecimal availableValue,
                   String currency, List<Lot> lots) {
    }
}
