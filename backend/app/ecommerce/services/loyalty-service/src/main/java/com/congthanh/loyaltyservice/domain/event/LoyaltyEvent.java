package com.congthanh.loyaltyservice.domain.event;

import com.congthanh.loyaltyservice.domain.model.Tier;

public sealed interface LoyaltyEvent {

    String customerId();

    record Earned(String customerId, String orderId, String lotId, long points) implements LoyaltyEvent {
    }

    record Confirmed(String customerId, String lotId, String orderId, long points) implements LoyaltyEvent {
    }

    record Redeemed(String customerId, String redemptionId, String orderId, long points) implements LoyaltyEvent {
    }

    record Reversed(String customerId, String orderId, long points, boolean confirmed, String reason)
            implements LoyaltyEvent {
    }

    record Expired(String customerId, String lotId, long points) implements LoyaltyEvent {
    }

    record TierChanged(String customerId, Tier from, Tier to) implements LoyaltyEvent {
    }
}
