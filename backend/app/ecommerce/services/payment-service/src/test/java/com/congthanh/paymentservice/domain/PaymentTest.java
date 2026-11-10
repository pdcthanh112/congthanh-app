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
        return Payment.initiate("pay-1", "o-1", "c-1", new Money(new BigDecimal("225000"), "VND"),
                PaymentMethod.STRIPE, "stripe", NOW);
    }

    @Test
    void minorUnitsFollowCurrencyFractionDigits() {
        assertThat(new Money(new BigDecimal("225000"), "VND").minorUnits()).isEqualTo(225000L);
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
    void redirectFlowCanBePaidOrDeclined() {
        Payment paid = payment();
        paid.actionRequired("vnp_1", "https://pay/1", NOW);
        assertThat(paid.redirectUrl()).isEqualTo("https://pay/1");
        paid.paidImmediately("vnp_1", NOW);
        assertThat(paid.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(paid.redirectUrl()).isNull();

        Payment declined = payment();
        declined.actionRequired("vnp_2", "https://pay/2", NOW);
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
