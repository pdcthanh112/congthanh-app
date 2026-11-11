package com.congthanh.paymentservice.domain;

import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.InstallmentPolicy;
import com.congthanh.paymentservice.domain.model.Money;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallmentPolicyTest {

    private static final String RETURN_URL = "https://shop.example/checkout/result";

    private static final InstallmentPolicy POLICY = new InstallmentPolicy(Map.of(
            PaymentMethod.AFFIRM, new InstallmentPolicy.Limit(new BigDecimal("50"), new BigDecimal("30000"), Duration.ofDays(30)),
            PaymentMethod.AFTERPAY, new InstallmentPolicy.Limit(new BigDecimal("1"), new BigDecimal("4000"), Duration.ofDays(13))),
            Set.of("usd"));

    private static Money usd(String amount) {
        return new Money(new BigDecimal(amount), "USD");
    }

    @Test
    void optionsFollowAmountRangeCurrencyAndConfiguredProviders() {
        assertThat(POLICY.options(usd("20.00"), m -> true)).extracting(InstallmentPolicy.Option::method)
                .containsExactly(PaymentMethod.AFTERPAY);
        assertThat(POLICY.options(usd("120.00"), m -> true)).extracting(InstallmentPolicy.Option::method)
                .containsExactlyInAnyOrder(PaymentMethod.AFFIRM, PaymentMethod.AFTERPAY);
        assertThat(POLICY.options(usd("120.00"), m -> m != PaymentMethod.AFFIRM)).extracting(InstallmentPolicy.Option::method)
                .containsExactly(PaymentMethod.AFTERPAY);
        assertThat(POLICY.options(new Money(new BigDecimal("120"), "EUR"), m -> true)).isEmpty();
    }

    @Test
    void checkRejectsDisabledOutOfRangeAndMissingReturnUrl() {
        assertThatCode(() -> POLICY.check(PaymentMethod.AFFIRM, usd("50.00"), RETURN_URL)).doesNotThrowAnyException();
        assertThatCode(() -> POLICY.check(PaymentMethod.CARD, usd("5.00"), null)).doesNotThrowAnyException();

        assertThatThrownBy(() -> POLICY.check(PaymentMethod.KLARNA, usd("100.00"), RETURN_URL))
                .isInstanceOf(PaymentException.class).extracting("kind").isEqualTo(PaymentException.Kind.UNSUPPORTED);
        assertThatThrownBy(() -> POLICY.check(PaymentMethod.AFFIRM, usd("49.99"), RETURN_URL))
                .isInstanceOf(PaymentException.class).extracting("kind").isEqualTo(PaymentException.Kind.UNSUPPORTED);
        assertThatThrownBy(() -> POLICY.check(PaymentMethod.AFFIRM, usd("100.00"), " "))
                .isInstanceOf(PaymentException.class).extracting("kind").isEqualTo(PaymentException.Kind.INVALID);
    }

    @Test
    void authorizationTtlOnlyForEnabledInstallments() {
        assertThat(POLICY.authorizationTtl(PaymentMethod.AFTERPAY)).isEqualTo(Duration.ofDays(13));
        assertThat(POLICY.authorizationTtl(PaymentMethod.CARD)).isNull();
        assertThat(InstallmentPolicy.disabled().options(usd("100"), m -> true)).isEmpty();
    }

    @Test
    void methodsExposeCategoryAndStripeType() {
        assertThat(PaymentMethod.KLARNA.isInstallment()).isTrue();
        assertThat(PaymentMethod.APPLE_PAY.category()).isEqualTo(PaymentMethod.Category.WALLET);
        assertThat(PaymentMethod.AFTERPAY.stripeType()).isEqualTo("afterpay_clearpay");
        assertThat(PaymentMethod.PAYPAL.stripeType()).isNull();
        assertThatThrownBy(() -> new InstallmentPolicy(Map.of(PaymentMethod.CARD,
                new InstallmentPolicy.Limit(BigDecimal.ONE, BigDecimal.TEN, null)), Set.of("USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
