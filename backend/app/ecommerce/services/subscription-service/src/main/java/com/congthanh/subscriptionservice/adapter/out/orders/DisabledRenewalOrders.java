package com.congthanh.subscriptionservice.adapter.out.orders;

import com.congthanh.subscriptionservice.application.port.out.RenewalOrders;
import com.congthanh.subscriptionservice.domain.model.Subscription;
import org.springframework.stereotype.Component;

/**
 * Tắt tạo đơn gia hạn ({@code SUBSCRIPTIONS_RENEWALS_ENABLED=false}, mặc định): service chỉ gửi nhắc, lịch giao giữ
 * nguyên, không charge ai. Bật thì dùng {@link CheckoutRenewalOrders} (ADR 0027).
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "subscriptions.renewals.enabled", havingValue = "false", matchIfMissing = true)
class DisabledRenewalOrders implements RenewalOrders {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String place(Subscription s, String idempotencyKey) {
        throw new Rejected("RENEWAL_ORDERS_DISABLED");
    }
}
