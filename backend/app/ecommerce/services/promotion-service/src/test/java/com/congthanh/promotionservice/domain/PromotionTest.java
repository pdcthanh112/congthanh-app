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

    private static BigDecimal vnd(String v) {
        return new BigDecimal(v);
    }

    private static Promotion promo(DiscountRule rule, int usageLimit, int perCustomer) {
        return Promotion.rehydrate(7L, "SALE10", PromotionType.VOUCHER, rule, usageLimit, perCustomer, 0,
                START, END, true, null, 0);
    }

    @Test
    void percentageIsCappedAndRoundedDown() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.PERCENTAGE, vnd("10"), "VND", vnd("100000"), vnd("50000"));
        assertThat(rule.discountFor(vnd("123456"))).isEqualByComparingTo("12345");
        assertThat(rule.discountFor(vnd("900000"))).isEqualByComparingTo("50000");

        DiscountRule usd = new DiscountRule(DiscountRule.Kind.PERCENTAGE, vnd("15"), "USD", null, null);
        assertThat(usd.discountFor(vnd("9.99"))).isEqualByComparingTo("1.49");
    }

    @Test
    void fixedAmountNeverExceedsSubtotal() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, vnd("30000"), "VND", null, null);
        assertThat(rule.discountFor(vnd("20000"))).isEqualByComparingTo("20000");
    }

    @Test
    void invalidRulesAreRejected() {
        assertThatThrownBy(() -> new DiscountRule(DiscountRule.Kind.PERCENTAGE, vnd("120"), "VND", null, null))
                .isInstanceOf(PromotionException.class);
        assertThatThrownBy(() -> new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, vnd("1"), "XYZ", null, null))
                .isInstanceOf(PromotionException.class);
    }

    @Test
    void quoteChecksWindowLimitsCurrencyAndMinimum() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, vnd("10000"), "VND", vnd("100000"), null);
        Promotion p = promo(rule, 0, 1);

        assertThat(p.quote(vnd("200000"), "VND", 0, NOW).valid()).isTrue();
        assertThat(p.quote(vnd("200000"), "VND", 0, START.minusSeconds(1)).reason()).isEqualTo("NOT_STARTED");
        assertThat(p.quote(vnd("200000"), "VND", 0, END).reason()).isEqualTo("EXPIRED");
        assertThat(p.quote(vnd("200000"), "VND", 1, NOW).reason()).isEqualTo("ALREADY_USED");
        assertThat(p.quote(vnd("200000"), "USD", 0, NOW).reason()).isEqualTo("CURRENCY");
        assertThat(p.quote(vnd("99999"), "VND", 0, NOW).reason()).isEqualTo("MIN_ORDER");
    }

    @Test
    void redeemConsumesUsageUntilExhaustedAndReleaseGivesItBack() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, vnd("10000"), "VND", null, null);
        Promotion p = promo(rule, 1, 0);

        Quote first = p.redeem(vnd("50000"), "VND", 0, "o-1", "c-1", NOW);
        Quote second = p.redeem(vnd("50000"), "VND", 0, "o-2", "c-2", NOW);

        assertThat(first.valid()).isTrue();
        assertThat(second.reason()).isEqualTo("EXHAUSTED");
        assertThat(p.usedCount()).isEqualTo(1);
        assertThat(p.pullEvents()).singleElement().isInstanceOf(PromotionEvent.PromotionRedeemed.class);

        p.releaseUse("o-1", "checkout cancelled");
        assertThat(p.usedCount()).isZero();
        assertThat(p.redeem(vnd("50000"), "VND", 0, "o-2", "c-2", NOW).valid()).isTrue();
    }

    @Test
    void cannotLowerLimitBelowUsage() {
        DiscountRule rule = new DiscountRule(DiscountRule.Kind.FIXED_AMOUNT, vnd("10000"), "VND", null, null);
        Promotion p = Promotion.rehydrate(7L, "SALE10", PromotionType.VOUCHER, rule, 10, 0, 5, START, END, true, null, 0);
        assertThatThrownBy(() -> p.update(PromotionType.VOUCHER, rule, 3, 0, START, END, null))
                .extracting("kind").isEqualTo(PromotionException.Kind.CONFLICT);
    }
}
