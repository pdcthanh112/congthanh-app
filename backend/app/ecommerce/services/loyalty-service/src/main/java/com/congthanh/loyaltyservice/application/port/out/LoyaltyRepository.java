package com.congthanh.loyaltyservice.application.port.out;

import com.congthanh.loyaltyservice.domain.model.Member;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoyaltyRepository {

    /** Khoá thành viên tới hết transaction (tạo mới nếu chưa có) cùng các lô còn hiệu lực hoặc tích trong ~13 tháng. */
    Member lock(String customerId);

    Optional<Member> find(String customerId);

    /** Lưu hạng, lô và ghi event vào outbox. */
    void save(Member m);

    Optional<String> customerOfOrder(String orderId);

    /** Thành viên có lô tới hạn xác nhận/hết hạn, hoặc lâu chưa tính hạng. */
    List<String> dueForHousekeeping(Instant now, int limit);

    void markHoused(String customerId, Instant now);

    void saveRedemption(Redemption r);

    Optional<Redemption> lockRedemption(String redemptionId);

    record Redemption(String id, String customerId, String orderId, long points, String status,
                      List<Member.Allocation> allocations) {
    }
}
