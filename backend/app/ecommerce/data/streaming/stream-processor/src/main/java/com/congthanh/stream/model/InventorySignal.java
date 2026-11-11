package com.congthanh.stream.model;

/** LEVEL = tồn khả dụng mới của một kho (StockLevelChanged); SOLD = số lượng đã chốt bán (InventoryCommitted). */
public class InventorySignal {
    public static final String LEVEL = "LEVEL";
    public static final String SOLD = "SOLD";

    public String productId = "";
    public String kind = "";
    public String warehouseId = "";
    public int quantity;
    public long ts;

    public InventorySignal() {}

    public InventorySignal(String productId, String kind, String warehouseId, int quantity, long ts) {
        this.productId = productId;
        this.kind = kind;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.ts = ts;
    }
}
