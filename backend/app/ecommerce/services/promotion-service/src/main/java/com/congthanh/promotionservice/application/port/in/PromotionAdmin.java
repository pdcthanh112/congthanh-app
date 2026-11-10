package com.congthanh.promotionservice.application.port.in;

import com.congthanh.promotionservice.domain.model.DiscountRule;
import com.congthanh.promotionservice.domain.model.Promotion;
import com.congthanh.promotionservice.domain.model.PromotionType;

import java.time.Instant;

public interface PromotionAdmin {

    Promotion create(PromotionSpec spec);

    Promotion update(long id, PromotionSpec spec);

    void deactivate(long id);

    Promotion byId(long id);

    Promotion byCode(String code);

    record PromotionSpec(String code, PromotionType type, DiscountRule rule, int usageLimit, int perCustomerLimit,
                         Instant startsAt, Instant endsAt, String description) {
    }
}
