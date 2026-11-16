package com.congthanh.loyaltyservice.domain;

import com.congthanh.loyaltyservice.domain.event.LoyaltyEvent;
import com.congthanh.loyaltyservice.domain.exception.LoyaltyException;
import com.congthanh.loyaltyservice.domain.model.Lot;
import com.congthanh.loyaltyservice.domain.model.Member;
import com.congthanh.loyaltyservice.domain.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    private static final Instant T0 = Instant.parse("2026-01-10T00:00:00Z");
    private static final Member.Rules RULES = new Member.Rules(java.time.Duration.ofDays(30), java.time.Duration.ofDays(45),
            java.time.Duration.ofDays(365), "USD", new java.math.BigDecimal("0.01"), 100);

    private static Member member(Tier tier) {
        return new Member("c-1", tier, List.of(), 1);
    }

    @Test
    void earnsPendingPointsByTierAndIsIdempotentPerOrder() {
        Member m = member(Tier.SILVER);
        Lot lot = m.earn("l-1", "o-1", new BigDecimal("199.99"), T0);
        m.earn("l-2", "o-1", new BigDecimal("199.99"), T0);

        assertThat(lot.points()).isEqualTo(249); // floor(199.99 × 1.25)
        assertThat(m.pending()).isEqualTo(249);
        assertThat(m.available()).isZero();
        assertThat(m.pullEvents()).hasSize(1);
    }

    @Test
    void pointsBecomeAvailableAfterReturnWindowFromDelivery() {
        Member m = member(Tier.MEMBER);
        m.earn("l-1", "o-1", new BigDecimal("100"), T0);
        m.delivered("o-1", T0.plus(Duration.ofDays(3)), RULES);

        m.confirmDue(T0.plus(Duration.ofDays(32)), RULES);
        assertThat(m.available()).isZero();
        m.confirmDue(T0.plus(Duration.ofDays(33)), RULES);
        assertThat(m.available()).isEqualTo(100);
        assertThat(m.pullEvents()).last().isInstanceOf(LoyaltyEvent.Confirmed.class);
    }

    @Test
    void partialRefundReversesProportionalPoints() {
        Member m = member(Tier.MEMBER);
        m.earn("l-1", "o-1", new BigDecimal("200"), T0);
        m.pullEvents();

        long reversed = m.reverse("o-1", new BigDecimal("50"), "RETURN r-1");
        assertThat(reversed).isEqualTo(50);
        assertThat(m.pending()).isEqualTo(150);
        assertThat(m.pullEvents()).singleElement()
                .isEqualTo(new LoyaltyEvent.Reversed("c-1", "o-1", 50, false, "RETURN r-1"));

        m.reverse("o-1", new BigDecimal("1000"), "CANCEL");
        assertThat(m.pending()).isZero();
        assertThat(m.lots().getFirst().status()).isEqualTo(Lot.Status.REVERSED);
    }

    @Test
    void redeemTakesSoonestExpiringLotsFirstAndReleaseRestores() {
        Member m = new Member("c-1", Tier.MEMBER, List.of(
                new Lot("late", "c-1", "o-1", 300, 300, Lot.Status.AVAILABLE, BigDecimal.TEN, T0, T0, T0.plus(Duration.ofDays(300))),
                new Lot("soon", "c-1", "o-2", 150, 150, Lot.Status.AVAILABLE, BigDecimal.TEN, T0, T0, T0.plus(Duration.ofDays(10)))), 1);

        List<Member.Allocation> a = m.reserve(200, RULES);
        assertThat(a).containsExactly(new Member.Allocation("soon", 150), new Member.Allocation("late", 50));
        assertThat(m.available()).isEqualTo(250);
        assertThat(m.value(200, RULES)).isEqualByComparingTo("2.00");

        m.release(a, T0);
        assertThat(m.available()).isEqualTo(450);
    }

    @Test
    void cannotRedeemBelowMinimumOrAboveBalance() {
        Member m = new Member("c-1", Tier.MEMBER, List.of(
                new Lot("l", "c-1", "o-1", 120, 120, Lot.Status.AVAILABLE, BigDecimal.TEN, T0, T0, T0.plus(Duration.ofDays(9)))), 1);
        assertThatThrownBy(() -> m.reserve(50, RULES)).isInstanceOf(LoyaltyException.class);
        assertThatThrownBy(() -> m.reserve(121, RULES)).isInstanceOf(LoyaltyException.class)
                .extracting("kind").isEqualTo(LoyaltyException.Kind.INSUFFICIENT_FUNDS);
    }

    @Test
    void expiredLotsAreRemovedAndTierFollowsSpend() {
        Instant now = T0.plus(Duration.ofDays(100));
        Member m = new Member("c-1", Tier.MEMBER, List.of(
                new Lot("a", "c-1", "o-1", 600, 600, Lot.Status.AVAILABLE, new BigDecimal("600"), T0, T0, T0.plus(Duration.ofDays(50))),
                new Lot("b", "c-1", "o-2", 1000, 1000, Lot.Status.AVAILABLE, new BigDecimal("1000"), T0, T0, now.plus(Duration.ofDays(200)))), 1);
        m.expireDue(now);
        assertThat(m.available()).isEqualTo(1000);

        m.retier(now);
        assertThat(m.tier()).isEqualTo(Tier.GOLD); // 600 + 1000 chi tiêu (lô đã hết hạn vẫn tính chi tiêu)
        assertThat(m.pullEvents()).extracting(e -> e.getClass().getSimpleName()).containsExactly("Expired", "TierChanged");
    }
}
