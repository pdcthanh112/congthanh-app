package com.congthanh.stream.model;

/** Tương tác với sản phẩm (thêm giỏ, đặt mua, wishlist) dùng tính trending. */
public class InteractionSignal {
    public static final String CART = "CART";
    public static final String ORDER = "ORDER";
    public static final String WISHLIST = "WISHLIST";

    public String productId = "";
    public String kind = "";
    public int units;
    public long ts;

    public InteractionSignal() {}

    public InteractionSignal(String productId, String kind, int units, long ts) {
        this.productId = productId;
        this.kind = kind;
        this.units = units;
        this.ts = ts;
    }
}
