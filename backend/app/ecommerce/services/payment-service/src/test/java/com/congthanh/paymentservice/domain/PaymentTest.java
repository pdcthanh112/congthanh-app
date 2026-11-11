package com.congthanh.paymentservice.domain;

import com.congthanh.paymentservice.domain.event.PaymentEvent;
import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.Money;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import com.congthanh.paymentservice.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    private static Payment payment() {
        return Payment.initiate("pay-1", "o-1", "c-1", new Money(new BigDecimal("89.99"), "USD"),
                PaymentMethod.CARD, "stripe", NOW);
    }

    @Test
    void minorUnitsFollowCurrencyFractionDigits() {
        assertThat(new Money(new BigDecimal("1500"), "JPY").minorUnits()).isEqualTo(1500L);
        assertThat(new Money(new BigDecimal("12.34"), "USD").minorUnits()).isEqualTo(1234L);
    }

    @Test
    void authorizeThenCaptureIsIdempotent() {
        Payment p = payment();
        p.authorized("pi_1", NOW);
        p.authorized("pi_1", NOW);
        p.captured(NOW);
        p.captured(NOW);

        assertThat(p.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(p.pullEvents()).singleElement().isInstanceOf(PaymentEvent.PaymentAuthorized.class);
    }

    @Test
    void installmentAuthorizationKeepsCaptureDeadlineAndMethodInEvent() {
        Payment p = Payment.initiate("pay-2", "o-2", "c-1", new Money(new BigDecimal("250.00"), "USD"),
                PaymentMethod.AFFIRM, "stripe", NOW);
        p.actionRequired("pi_2", "https://affirm/checkout", NOW);
        p.authorized("pi_2", NOW, NOW.plusSeconds(30 * 86400));

        assertThat(p.authorizationExpiresAt()).isEqualTo(NOW.plusSeconds(30 * 86400));
        assertThat(p.pullEvents()).singleElement().isEqualTo(new PaymentEvent.PaymentAuthorized(
                "pay-2", "o-2", new Money(new BigDecimal("250.00"), "USD"), "stripe", "AFFIRM"));
    }

    @Test
    void redirectFlowCanBeApprovedOrDeclined() {
        Payment paid = payment();
        paid.actionRequired("pp_1", "https://pay/1", NOW);
        assertThat(paid.redirectUrl()).isEqualTo("https://pay/1");
        paid.authorized("pp_1", NOW);
        assertThat(paid.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(paid.redirectUrl()).isNull();

        Payment declined = payment();
        declined.actionRequired("pp_2", "https://pay/2", NOW);
        declined.declined("insufficient funds", NOW);
        assertThat(declined.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(declined.pullEvents()).singleElement()
                .isEqualTo(new PaymentEvent.PaymentFailed("o-1", "insufficient funds"));
    }

    @Test
    void cannotCaptureWithoutAuthorization() {
        assertThatThrownBy(() -> payment().captured(NOW))
                .isInstanceOf(PaymentException.class)
                .extracting("kind").isEqualTo(PaymentException.Kind.CONFLICT);
    }

    @Test
    void capturedPaymentCanBeRefundedOnce() {
        Payment p = payment();
        p.authorized("pi_1", NOW);
        p.captured(NOW);
        p.pullEvents();
        p.refunded(NOW);
        p.refunded(NOW);

        assertThat(p.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(p.pullEvents()).singleElement().isInstanceOf(PaymentEvent.PaymentRefunded.class);
    }
}
