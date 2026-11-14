package com.congthanh.subscriptionservice.domain.event;

import com.congthanh.subscriptionservice.domain.model.Subscription;
import com.congthanh.subscriptionservice.domain.model.SubscriptionStatus;

import java.time.Instant;

public sealed interface SubscriptionEvent {

    Subscription subscription();

    record Created(Subscription subscription) implements SubscriptionEvent {
    }

    record RenewalUpcoming(Subscription subscription) implements SubscriptionEvent {
    }

    record OrderPlaced(Subscription subscription, int cycle, String orderId) implements SubscriptionEvent {
    }

    record RenewalFailed(Subscription subscription, int cycle, int attempt, String reason, Instant nextAttemptAt)
            implements SubscriptionEvent {
    }

    record StatusChanged(Subscription subscription, SubscriptionStatus from, SubscriptionStatus to, String reason)
            implements SubscriptionEvent {
    }
}
