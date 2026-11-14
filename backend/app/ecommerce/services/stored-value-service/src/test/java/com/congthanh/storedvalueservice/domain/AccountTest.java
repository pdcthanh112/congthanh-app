package com.congthanh.storedvalueservice.domain;

import com.congthanh.storedvalueservice.domain.exception.StoredValueException;
import com.congthanh.storedvalueservice.domain.model.Account;
import com.congthanh.storedvalueservice.domain.model.Hold;
import com.congthanh.storedvalueservice.domain.model.Movement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");

    private static Account funded(String amount) {
        Account a = Account.open("acc-1", "c-1", Account.Kind.STORE_CREDIT, "usd", NOW);
        a.credit(new BigDecimal(amount), "RETURN_REFUND", "ret-1", NOW);
        a.pullMovements();
        return a;
    }

    @Test
    void holdReducesAvailableAndCaptureReducesBalance() {
        Account a = funded("50.00");
        Movement h = a.hold("h-1", "o-1", new BigDecimal("30"), NOW);
        assertThat(a.available()).isEqualByComparingTo("20.00");
        assertThat(a.balance()).isEqualByComparingTo("50.00");

        a.capture(new Hold("h-1", "acc-1", "o-1", h.amount(), Hold.Status.HELD, NOW), NOW);
        assertThat(a.balance()).isEqualByComparingTo("20.00");
        assertThat(a.held()).isEqualByComparingTo("0");
        assertThat(a.pullMovements()).extracting(Movement::type).containsExactly(Movement.Type.HOLD, Movement.Type.DEBIT);
    }

    @Test
    void cannotHoldMoreThanAvailable() {
        Account a = funded("20.00");
        a.hold("h-1", "o-1", new BigDecimal("15"), NOW);
        assertThatThrownBy(() -> a.hold("h-2", "o-2", new BigDecimal("5.01"), NOW))
                .isInstanceOf(StoredValueException.class)
                .extracting("kind").isEqualTo(StoredValueException.Kind.INSUFFICIENT_FUNDS);
    }

    @Test
    void releaseGivesHeldMoneyBack() {
        Account a = funded("20.00");
        Movement h = a.hold("h-1", "o-1", new BigDecimal("15"), NOW);
        a.release(new Hold("h-1", "acc-1", "o-1", h.amount(), Hold.Status.HELD, NOW), "EXPIRED", NOW);
        assertThat(a.available()).isEqualByComparingTo("20.00");
    }

    @Test
    void frozenAccountCanReceiveButNotSpend() {
        Account a = funded("20.00");
        a.freeze();
        a.credit(new BigDecimal("5"), "GOODWILL", "t-9", NOW);
        assertThat(a.balance()).isEqualByComparingTo("25.00");
        assertThatThrownBy(() -> a.hold("h", "o", BigDecimal.ONE, NOW)).isInstanceOf(StoredValueException.class);
    }

    @Test
    void amountsMustBePositiveAndFitTheCurrency() {
        Account a = funded("20.00");
        assertThatThrownBy(() -> a.credit(new BigDecimal("0"), "GOODWILL", "x", NOW)).isInstanceOf(StoredValueException.class);
        assertThatThrownBy(() -> a.credit(new BigDecimal("1.005"), "GOODWILL", "x", NOW)).isInstanceOf(StoredValueException.class);
    }

    @Test
    void holdStateChangesAreOneWay() {
        Hold h = new Hold("h", "a", "o", BigDecimal.TEN, Hold.Status.HELD, NOW);
        assertThatThrownBy(() -> h.captured().released()).isInstanceOf(StoredValueException.class);
        assertThatThrownBy(() -> h.released().captured()).isInstanceOf(StoredValueException.class);
        assertThat(h.expired(NOW.plusSeconds(1))).isTrue();
    }
}
