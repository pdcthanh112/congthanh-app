package com.congthanh.subscriptionservice.application.port.out;

import com.congthanh.subscriptionservice.domain.model.Subscription;

/**
 * Tạo đơn cho một kỳ gia hạn (giá hiện hành, thuế, phí ship, charge off-session bằng phương thức đã lưu).
 * Idempotent theo {@code idempotencyKey}.
 */
public interface RenewalOrders {

    /** false = chưa bật tạo đơn tự động; service chỉ gửi nhắc và giữ nguyên lịch. */
    boolean enabled();

    /** @return order id; thất bại nghiệp vụ (thẻ bị từ chối, hết hàng...) ném {@link Rejected} */
    String place(Subscription s, String idempotencyKey);

    final class Rejected extends RuntimeException {
        public Rejected(String reason) {
            super(reason);
        }
    }
}
