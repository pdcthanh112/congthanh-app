package com.congthanh.subscriptionservice.domain;

import com.congthanh.subscriptionservice.domain.event.SubscriptionEvent;
import com.congthanh.subscriptionservice.domain.exception.SubscriptionException;
import com.congthanh.subscriptionservice.domain.model.Line;
import com.congthanh.subscriptionservice.domain.model.Subscription;
import com.congthanh.subscriptionservice.domain.model.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final Instant NOW = Instant.parse("2026-11-14T15:00:00Z");
    private static final Subscription.Schedule S = Subscription.Schedule.defaults();

    private static Subscription sub() {
        Subscription s = Subscription.create("s-1", "c-1", List.of(new Line("p-1", 2)), 30, TODAY.plusDays(10), "pm_1",
                "USD", "a-1", TODAY, NOW);
        s.pullEvents();
        return s;
    }

    @Test
    void validatesIntervalFirstDeliveryAndPaymentMethod() {
        assertThatThrownBy(() -> Subscription.create("s", "c", List.of(new Line("p", 1)), 5, TODAY.plusDays(10), "pm", "USD", null, TODAY, NOW))
                .isInstanceOf(SubscriptionException.class);
        assertThatThrownBy(() -> Subscription.create("s", "c", List.of(new Line("p", 1)), 30, TODAY.plusDays(1), "pm", "USD", null, TODAY, NOW))
                .isInstanceOf(SubscriptionException.class);
        assertThatThrownBy(() -> Subscription.create("s", "c", List.of(new Line("p", 1)), 30, TODAY.plusDays(10), "", "USD", null, TODAY, NOW))
                .isInstanceOf(SubscriptionException.class);
    }

    @Test
    void remindsOncePerCycleThreeDaysBefore() {
        Subscription s = sub();
        assertThat(s.reminderDue(TODAY.plusDays(6), S)).isFalse();
        assertThat(s.reminderDue(TODAY.plusDays(7), S)).isTrue();
        s.reminded();
        assertThat(s.reminderDue(TODAY.plusDays(8), S)).isFalse();
    }

    @Test
    void renewalAdvancesScheduleAndSkipMovesDate() {
        Subscription s = sub();
        assertThat(s.renewalDue(TODAY.plusDays(7), NOW, S)).isFalse();
        assertThat(s.renewalDue(TODAY.plusDays(8), NOW, S)).isTrue();
        s.renewed("o-1");
        assertThat(s.cycle()).isEqualTo(2);
        assertThat(s.nextDeliveryDate()).isEqualTo(TODAY.plusDays(40));

        s.skipNext();
        assertThat(s.nextDeliveryDate()).isEqualTo(TODAY.plusDays(70));
    }

    @Test
    void failedRenewalsRetryWithBackoffThenCancel() {
        Subscription s = sub();
        String firstKey = s.renewalKey();
        s.renewalFailed("card_declined", NOW, S);
        assertThat(s.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(s.renewalKey()).isNotEqualTo(firstKey);
        assertThat(s.renewalDue(TODAY, NOW, S)).isFalse();
        assertThat(s.renewalDue(TODAY.plusDays(1), NOW.plusSeconds(86_400), S)).isTrue();

        for (int i = 0; i < 4; i++) {
            s.renewalFailed("card_declined", NOW, S);
        }
        assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(s.cancelReason()).startsWith("RENEWAL_FAILED");
    }

    @Test
    void successAfterPastDueReactivates() {
        Subscription s = sub();
        s.renewalFailed("card_declined", NOW, S);
        s.pullEvents();
        s.renewed("o-9");
        assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.pullEvents()).extracting(e -> e.getClass().getSimpleName()).containsExactly("OrderPlaced", "StatusChanged");
    }

    @Test
    void pauseResumeAndCancelAnytime() {
        Subscription s = sub();
        s.pause(TODAY.plusDays(60), TODAY);
        assertThat(s.renewalDue(TODAY.plusDays(10), NOW, S)).isFalse();
        // hết tạm dừng: tự hoạt động lại và ngày giao nhảy qua các kỳ đã lỡ
        s.renewalDue(TODAY.plusDays(60), NOW, S);
        assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.nextDeliveryDate()).isAfter(TODAY.plusDays(62));

        s.cancel("too much product");
        s.cancel("again");
        assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(s.pullEvents()).filteredOn(e -> e instanceof SubscriptionEvent.StatusChanged).hasSize(3);
        assertThatThrownBy(s::skipNext).isInstanceOf(SubscriptionException.class);
    }

    @Test
    void onlyOwnerSeesTheSubscription() {
        assertThatThrownBy(() -> sub().requireOwner("c-2")).isInstanceOf(SubscriptionException.class)
                .extracting("kind").isEqualTo(SubscriptionException.Kind.NOT_FOUND);
    }

    @Test
    void cancelledRenewalOrderRollsBackTheCycleIntoDunning() {
        Subscription s = sub();
        s.renewed("o-1");
        LocalDate next = s.nextDeliveryDate();
        s.pullEvents();

        s.renewalOrderCancelled(1, "card_declined", NOW, S);
        assertThat(s.cycle()).isEqualTo(1);
        assertThat(s.nextDeliveryDate()).isEqualTo(next.minusDays(30));
        assertThat(s.status()).isEqualTo(SubscriptionStatus.PAST_DUE);

        s.renewalOrderCancelled(1, "again", NOW, S); // event giao lại / không phải kỳ vừa gia hạn
        assertThat(s.attempts()).isEqualTo(1);
    }
}
