package com.congthanh.subscriptionservice.application.port.in;

import com.congthanh.subscriptionservice.domain.model.Line;
import com.congthanh.subscriptionservice.domain.model.Subscription;
import com.congthanh.subscriptionservice.domain.model.SubscriptionStatus;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionUseCases {

    Subscription create(String customerId, List<Line> lines, int intervalDays, LocalDate firstDelivery,
                        String paymentMethodRef, String currency, String addressId);

    List<Subscription> mine(String customerId);

    Subscription get(String id, String caller, boolean staff);

    Subscription change(String id, String caller, List<Line> lines, Integer intervalDays);

    Subscription skip(String id, String caller);

    Subscription pause(String id, String caller, LocalDate until);

    Subscription resume(String id, String caller);

    Subscription cancel(String id, String caller, String reason);

    List<Subscription> byStatus(SubscriptionStatus status, int limit);

    /** Việc định kỳ: gửi nhắc, tạo đơn tới hạn, thử lại đơn lỗi. @return số gói đã xử lý */
    int tick();

    /** Đơn gia hạn bị huỷ trong saga (thẻ từ chối, hết hàng): quay lại kỳ đó và vào dunning. */
    void renewalOrderCancelled(String orderId, String reason);
}
