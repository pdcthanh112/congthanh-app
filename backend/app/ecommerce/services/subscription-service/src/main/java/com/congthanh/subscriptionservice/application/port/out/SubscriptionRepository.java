package com.congthanh.subscriptionservice.application.port.out;

import com.congthanh.subscriptionservice.domain.model.Subscription;
import com.congthanh.subscriptionservice.domain.model.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository {

    /** Lưu (khoá lạc quan theo version) và ghi event vào outbox cùng transaction. */
    void save(Subscription s);

    Optional<Subscription> findById(String id);

    List<Subscription> byCustomer(String customerId);

    List<Subscription> byStatus(SubscriptionStatus status, int limit);

    /**
     * Gói có việc phải làm (nhắc, tạo đơn, thử lại, hết tạm dừng), khoá bằng FOR UPDATE SKIP LOCKED để nhiều pod
     * chia việc mà không làm trùng.
     */
    List<Subscription> claimDue(LocalDate horizon, Instant now, int limit);

    void recordRenewalOrder(String orderId, String subscriptionId, int cycle);

    /** Gói và kỳ của một đơn gia hạn; trống nếu đơn không phải đơn gia hạn. */
    Optional<RenewalOrder> renewalOrder(String orderId);

    record RenewalOrder(String orderId, String subscriptionId, int cycle) {
    }
}
