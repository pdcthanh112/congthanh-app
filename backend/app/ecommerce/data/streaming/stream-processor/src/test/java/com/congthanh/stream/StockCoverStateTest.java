package com.congthanh.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.congthanh.stream.logic.StockCoverState;
import com.congthanh.stream.model.InventorySignal;
import org.junit.jupiter.api.Test;

class StockCoverStateTest {
    static final long H = 3_600_000L;

    static InventorySignal level(String wh, int qty, long ts) {
        return new InventorySignal("p1", InventorySignal.LEVEL, wh, qty, ts);
    }

    static InventorySignal sold(int qty, long ts) {
        return new InventorySignal("p1", InventorySignal.SOLD, "w1", qty, ts);
    }

    @Test
    void sumsWarehousesAndComputesHoursOfCover() {
        StockCoverState s = new StockCoverState();
        s.apply(level("w1", 30, 0), 48);
        s.apply(level("w2", 18, 1), 48);
        StockCoverState.Cover c = s.apply(sold(24, 2), 48); // 1 sp/giờ
        assertThat(c.available).isEqualTo(48);
        assertThat(c.unitsSold24h).isEqualTo(24);
        assertThat(c.hoursOfCover).isEqualTo(48.0);
        assertThat(c.atRisk).isFalse();
    }

    @Test
    void flagsRiskOnceWhenCoverDropsBelowThreshold() {
        StockCoverState s = new StockCoverState();
        s.apply(level("w1", 100, 0), 48);
        StockCoverState.Cover c1 = s.apply(sold(240, H), 48); // 10/giờ → 10 giờ phủ
        assertThat(c1.atRisk).isTrue();
        assertThat(c1.riskChanged).isTrue();

        StockCoverState.Cover c2 = s.apply(level("w1", 90, 2 * H), 48);
        assertThat(c2.atRisk).isTrue();
        assertThat(c2.riskChanged).isFalse();

        StockCoverState.Cover c3 = s.apply(level("w1", 5_000, 3 * H), 48);
        assertThat(c3.atRisk).isFalse();
        assertThat(c3.riskChanged).isTrue();
    }

    @Test
    void noSalesMeansUnknownCoverAndNoRisk() {
        StockCoverState s = new StockCoverState();
        StockCoverState.Cover c = s.apply(level("w1", 0, 0), 48);
        assertThat(c.hoursOfCover).isEqualTo(-1);
        assertThat(c.atRisk).isFalse();
    }

    @Test
    void salesWithoutAnyLevelDoNotRaiseRisk() {
        StockCoverState s = new StockCoverState();
        assertThat(s.apply(sold(10, 0), 48).atRisk).isFalse();
    }

    @Test
    void salesOlderThanOneDayExpire() {
        StockCoverState s = new StockCoverState();
        s.apply(level("w1", 10, 0), 48);
        s.apply(sold(100, 0), 48);
        StockCoverState.Cover c = s.apply(level("w1", 10, 25 * H), 48);
        assertThat(c.unitsSold24h).isZero();
        assertThat(c.atRisk).isFalse();
    }
}
