package com.congthanh.promotionservice.domain;

import com.congthanh.promotionservice.domain.event.PromotionEvent;
import com.congthanh.promotionservice.domain.exception.PromotionException;
import com.congthanh.promotionservice.domain.model.DiscountRule;
import com.congthanh.promotionservice.domain.model.Promotion;
import com.congthanh.promotionservice.domain.model.PromotionType;
import com.congthanh.promotionservice.domain.model.Quote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionTest {

    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    private static BigDecimal amount(String v) {
        return new BigDecimal(v);
    }

    private static Promotion promo(DiscountRule rule, int usageLimit, int perCustomer) {
        return Promotion.rehydrate(7L, "SALE10", PromotionType.VOUCHER, rule, usageLimit, perCustomer, 0,
                START, END, true, null, 0);
    }

    @Test
    void percentageIsCappedAndRoundedDown() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.PERCENTAGE, amount("10"), "USD", amount("100"), amount("50"));
        assertThat(rule.discountFor(amount("123.45"))).isEqualByComparingTo("12.34");
        assertThat(rule.discountFor(amount("900"))).isEqualByComparingTo("50");

        DiscountRule jpy = new DiscountRule(DiscountRule.Kind.PERCENTAGE, amount("15"), "JPY", null, null);
        assertThat(jpy.discountFor(amount("999"))).isEqualByComparingTo("149");
    }

    @Test
    void fixedAmountNeverExceedsSubtotal() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, amount("30"), "USD", null, null);
        assertThat(rule.discountFor(amount("20"))).isEqualByComparingTo("20");
    }

    @Test
    void invalidRulesAreRejected() {
        assertThatThrownBy(() -> new DiscountRule(DiscountRule.Kind.PERCENTAGE, amount("120"), "USD", null, null))
                .isInstanceOf(PromotionException.class);
        assertThatThrownBy(() -> new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, amount("1"), "XYZ", null, null))
                .isInstanceOf(PromotionException.class);
    }

    @Test
    void quoteChecksWindowLimitsCurrencyAndMinimum() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, amount("10"), "USD", amount("100"), null);
        Promotion p = promo(rule, 0, 1);

        assertThat(p.quote(amount("200"), "USD", 0, NOW).valid()).isTrue();
        assertThat(p.quote(amount("200"), "USD", 0, START.minusSeconds(1)).reason()).isEqualTo("NOT_STARTED");
        assertThat(p.quote(amount("200"), "USD", 0, END).reason()).isEqualTo("EXPIRED");
        assertThat(p.quote(amount("200"), "USD", 1, NOW).reason()).isEqualTo("ALREADY_USED");
        assertThat(p.quote(amount("200"), "EUR", 0, NOW).reason()).isEqualTo("CURRENCY");
        assertThat(p.quote(amount("99.99"), "USD", 0, NOW).reason()).isEqualTo("MIN_ORDER");
    }

    @Test
    void redeemConsumesUsageUntilExhaustedAndReleaseGivesItBack() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, amount("10"), "USD", null, null);
        Promotion p = promo(rule, 1, 0);

        Quote first = p.redeem(amount("50"), "USD", 0, "o-1", "c-1", NOW);
        Quote second = p.redeem(amount("50"), "USD", 0, "o-2", "c-2", NOW);

        assertThat(first.valid()).isTrue();
        assertThat(second.reason()).isEqualTo("EXHAUSTED");
        assertThat(p.usedCount()).isEqualTo(1);
        assertThat(p.pullEvents()).singleElement().isInstanceOf(PromotionEvent.PromotionRedeemed.class);

        p.releaseUse("o-1", "checkout cancelled");
        assertThat(p.usedCount()).isZero();
        assertThat(p.redeem(amount("50"), "USD", 0, "o-2", "c-2", NOW).valid()).isTrue();
    }

    @Test
    void cannotLowerLimitBelowUsage() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, amount("10"), "USD", null, null);
        Promotion p = Promotion.rehydrate(7L, "SALE10", PromotionType.VOUCHER, rule, 10, 0, 5, START, END, true, null, 0);
        assertThatThrownBy(() -> p.update(PromotionType.VOUCHER, rule, 3, 0, START, END, null))
                .extracting("kind").isEqualTo(PromotionException.Kind.CONFLICT);
    }
}
