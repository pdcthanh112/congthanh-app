package com.congthanh.promotionservice.application.port.out;

import com.congthanh.promotionservice.domain.model.Promotion;
import com.congthanh.promotionservice.domain.model.Redemption;

import java.util.Optional;

public interface PromotionRepository {

    Optional<Promotion> findById(long id);

    Optional<Promotion> findByCode(String code);

    /** SELECT ... FOR UPDATE: tuần tự hoá các lượt redeem cùng một voucher. Phải gọi trong transaction. */
    Optional<Promotion> lockByCode(String code);

    Optional<Promotion> lockById(long id);

    /** Lưu + ghi event vào outbox cùng transaction; trả về bản có id. */
    Promotion save(Promotion promotion);

    Optional<Redemption> findRedemption(String orderId);

    int countActiveRedemptions(long promotionId, String customerId);

    void saveRedemption(Redemption redemption);
}
