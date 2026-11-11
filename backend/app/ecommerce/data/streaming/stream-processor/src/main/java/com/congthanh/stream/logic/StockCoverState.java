package com.congthanh.stream.logic;

import com.congthanh.stream.model.InventorySignal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tồn theo kho + lượng bán 24 giờ của một sản phẩm (state Flink, POJO). */
public class StockCoverState {
    static final long DAY = 24 * 3_600_000L;

    public Map<String, Integer> availableByWarehouse = new HashMap<>();
    public List<Sale> sales = new ArrayList<>();
    public long latest;
    public boolean atRisk;
    public boolean seenLevel;

    public static class Sale {
        public long ts;
        public int quantity;

        public Sale() {}

        Sale(long ts, int quantity) {
            this.ts = ts;
            this.quantity = quantity;
        }
    }

    public static class Cover {
        public int available;
        public int unitsSold24h;
        public double hoursOfCover;
        public boolean atRisk;
        public boolean riskChanged;
        public long computedAt;
    }

    /**
     * Áp một tín hiệu và trả về độ phủ tồn mới. Rủi ro hết hàng khi đã bán trong 24 giờ qua và lượng tồn chỉ đủ
     * dưới {@code thresholdHours} giờ. Chưa thấy StockLevelChanged nào thì chưa đánh giá (tránh báo động giả).
     */
    public Cover apply(InventorySignal s, double thresholdHours) {
        latest = Math.max(latest, s.ts);
        if (InventorySignal.LEVEL.equals(s.kind)) {
            availableByWarehouse.put(s.warehouseId, Math.max(0, s.quantity));
            seenLevel = true;
        } else if (InventorySignal.SOLD.equals(s.kind) && s.quantity > 0 && s.ts >= latest - DAY) {
            sales.add(new Sale(s.ts, s.quantity));
        }
        long from = latest - DAY;
        sales.removeIf(x -> x.ts < from);

        Cover c = new Cover();
        c.available = availableByWarehouse.values().stream().mapToInt(Integer::intValue).sum();
        c.unitsSold24h = sales.stream().mapToInt(x -> x.quantity).sum();
        c.hoursOfCover = c.unitsSold24h == 0 ? -1 : c.available / (c.unitsSold24h / 24.0);
        c.atRisk = seenLevel && c.unitsSold24h > 0 && c.hoursOfCover < thresholdHours;
        c.riskChanged = c.atRisk != atRisk;
        c.computedAt = latest;
        atRisk = c.atRisk;
        return c;
    }
}
