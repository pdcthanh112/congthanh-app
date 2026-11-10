package com.congthanh.promotionservice.domain.event;

import java.time.Instant;

public sealed interface PromotionEvent {

    record PromotionActivated(Long promotionId, String code, Instant startsAt, Instant endsAt) implements PromotionEvent {
    }

    record PromotionExpired(Long promotionId) implements PromotionEvent {
    }

    record PromotionRedeemed(Long promotionId, String code, String orderId, String customerId) implements PromotionEvent {
    }

    record RedemptionCancelled(Long promotionId, String orderId, String reason) implements PromotionEvent {
    }
}
