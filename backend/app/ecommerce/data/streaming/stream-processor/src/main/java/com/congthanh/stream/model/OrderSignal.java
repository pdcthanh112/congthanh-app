package com.congthanh.stream.model;

/** Tín hiệu từ order/payment events. customerId rỗng với event chỉ có orderId (PaymentFailed, OrderCancelled). */
public class OrderSignal {
    public static final String PLACED = "PLACED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    public String orderId = "";
    public String customerId = "";
    public String kind = "";
    public long amountMinor;
    public String currency = "";
    public String addressFp = "";
    public long ts;

    public OrderSignal() {}

    public OrderSignal(String orderId, String customerId, String kind, long amountMinor, String currency, String addressFp, long ts) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.kind = kind;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.addressFp = addressFp;
        this.ts = ts;
    }

    public OrderSignal withCustomer(String customerId) {
        return new OrderSignal(orderId, customerId, kind, amountMinor, currency, addressFp, ts);
    }

    @Override
    public String toString() {
        return "OrderSignal{" + kind + " order=" + orderId + " customer=" + customerId + " ts=" + ts + "}";
    }
}
