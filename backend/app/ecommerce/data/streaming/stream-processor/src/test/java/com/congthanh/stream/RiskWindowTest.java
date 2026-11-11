package com.congthanh.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.congthanh.stream.logic.RiskWindow;
import com.congthanh.stream.model.OrderSignal;
import org.junit.jupiter.api.Test;

class RiskWindowTest {
    static final long H = RiskWindow.HOUR;

    static OrderSignal placed(long ts, long amount, String currency, String fp) {
        return new OrderSignal("o" + ts, "c1", OrderSignal.PLACED, amount, currency, fp, ts);
    }

    static OrderSignal of(String kind, long ts) {
        return new OrderSignal("o" + ts, "c1", kind, 0, "", "", ts);
    }

    @Test
    void countsOnlyLastHourForOrdersAndFailures() {
        RiskWindow w = new RiskWindow();
        w.add(placed(0, 100_000, "USD", "a"));
        w.add(of(OrderSignal.PAYMENT_FAILED, 10));
        w.add(placed(2 * H, 200_000, "USD", "b"));
        w.add(of(OrderSignal.PAYMENT_FAILED, 2 * H + 1));
        w.add(placed(2 * H + 5, 300_000, "USD", "b"));

        RiskWindow.Features f = w.features();
        assertThat(f.orders1h).isEqualTo(2);
        assertThat(f.orderValue1h).isEqualTo(500_000);
        assertThat(f.paymentFailures1h).isEqualTo(1);
        assertThat(f.distinctAddresses24h).isEqualTo(2); // a và b vẫn trong 24 giờ
        assertThat(f.computedAt).isEqualTo(2 * H + 5);
    }

    @Test
    void valueSumsOnlyLatestCurrency() {
        RiskWindow w = new RiskWindow();
        w.add(placed(0, 5_000, "EUR", ""));
        w.add(placed(1, 1_000_000, "USD", ""));
        RiskWindow.Features f = w.features();
        assertThat(f.currency).isEqualTo("USD");
        assertThat(f.orderValue1h).isEqualTo(1_000_000);
        assertThat(f.orders1h).isEqualTo(2);
    }

    @Test
    void evictsAfterOneDayAndIgnoresTooOldEvents() {
        RiskWindow w = new RiskWindow();
        w.add(of(OrderSignal.CANCELLED, 0));
        w.add(of(OrderSignal.CANCELLED, 10 * H));
        w.add(of(OrderSignal.CANCELLED, 25 * H));
        assertThat(w.features().cancellations24h).isEqualTo(2);

        w.add(of(OrderSignal.CANCELLED, H - 1)); // cũ hơn 24h so với event mới nhất → bỏ
        assertThat(w.features().cancellations24h).isEqualTo(2);
    }

    @Test
    void outOfOrderEventsInsideWindowStillCount() {
        RiskWindow w = new RiskWindow();
        w.add(of(OrderSignal.PAYMENT_FAILED, 3 * H));
        w.add(of(OrderSignal.PAYMENT_FAILED, 3 * H - 10)); // đến muộn nhưng vẫn trong 1 giờ
        assertThat(w.features().paymentFailures1h).isEqualTo(2);
        assertThat(w.latest).isEqualTo(3 * H);
    }
}
