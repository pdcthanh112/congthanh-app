package com.congthanh.orderservice.domain.model;

import com.congthanh.orderservice.domain.event.OrderEvent;
import com.congthanh.orderservice.domain.exception.OrderException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate Order. Mọi thay đổi trạng thái đi qua method có kiểm tra chuyển trạng thái hợp lệ
 * và đều idempotent (gọi lại với cùng tham số không làm gì), vì saga Temporal có thể retry activity.
 */
public final class Order {

    private final String id;
    private final String orderCode;
    private final String customerId;
    private final List<OrderLine> lines;
    private final Money subtotal;
    private final Money discount;
    private final Money total;
    private final String promotionCode;
    private final CheckoutDetails checkout;
    private final Address shippingAddress;
    private final Address billingAddress;
    private final String note;
    private final Instant placedAt;
    private final long version;

    private OrderStatus status;
    private String reservationId;
    private String paymentId;
    private String cancelReason;
    private Instant updatedAt;

    private final List<OrderEvent> events = new ArrayList<>();

    private Order(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.orderCode = Objects.requireNonNull(b.orderCode, "orderCode");
        this.customerId = Objects.requireNonNull(b.customerId, "customerId");
        this.lines = List.copyOf(b.lines);
        this.subtotal = b.subtotal;
        this.discount = b.discount;
        this.total = b.total;
        this.promotionCode = b.promotionCode;
        this.checkout = b.checkout;
        this.shippingAddress = b.shippingAddress;
        this.billingAddress = b.billingAddress;
        this.note = b.note;
        this.placedAt = b.placedAt;
        this.version = b.version;
        this.status = b.status;
        this.reservationId = b.reservationId;
        this.paymentId = b.paymentId;
        this.cancelReason = b.cancelReason;
        this.updatedAt = b.updatedAt;
    }

    /** Đặt đơn mới: tính tổng từ các dòng hàng và giảm giá đã báo giá, phát OrderPlaced. */
    public static Order place(String id, String orderCode, String customerId, List<OrderLine> lines,
                              String promotionCode, Money discount, CheckoutDetails checkout,
                              Address shippingAddress, Address billingAddress, String note,
                              String checkoutWorkflowId, Instant now) {
        if (customerId == null || customerId.isBlank()) {
            throw OrderException.invalid("customerId is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw OrderException.invalid("order must have at least one line");
        }
        if (shippingAddress == null) {
            throw OrderException.invalid("shippingAddress is required");
        }
        String currency = lines.getFirst().unitPrice().currency();
        Money subtotal = Money.zero(currency);
        for (OrderLine line : lines) {
            subtotal = subtotal.plus(line.total());
        }
        Money appliedDiscount = discount == null ? Money.zero(currency) : discount;
        if (appliedDiscount.isNegative() || appliedDiscount.isGreaterThan(subtotal)) {
            throw OrderException.invalid("discount must be between 0 and the subtotal");
        }
        Order order = new Builder()
                .id(id).orderCode(orderCode).customerId(customerId).lines(lines)
                .subtotal(subtotal).discount(appliedDiscount).total(subtotal.minus(appliedDiscount))
                .promotionCode(promotionCode == null || promotionCode.isBlank() ? null : promotionCode)
                .checkout(checkout).shippingAddress(shippingAddress)
                .billingAddress(billingAddress == null ? shippingAddress : billingAddress)
                .note(note).placedAt(now).updatedAt(now).status(OrderStatus.PENDING)
                .build();
        order.events.add(new OrderEvent.OrderPlaced(id, customerId, order.lines, order.total, checkoutWorkflowId));
        return order;
    }

    public void markReserved(String reservationId, Instant now) {
        if (status == OrderStatus.RESERVED && Objects.equals(this.reservationId, reservationId)) {
            return;
        }
        requireStatus(OrderStatus.PENDING, "mark reserved");
        this.reservationId = reservationId;
        this.status = OrderStatus.RESERVED;
        this.updatedAt = now;
    }

    public void confirm(String paymentId, Instant now) {
        if (status == OrderStatus.CONFIRMED) {
            return;
        }
        requireStatus(OrderStatus.RESERVED, "confirm");
        this.paymentId = paymentId;
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = now;
        events.add(new OrderEvent.OrderConfirmed(id, paymentId));
    }

    public void cancel(String reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        if (status == OrderStatus.CONFIRMED) {
            throw OrderException.conflict("order " + orderCode + " is confirmed; use the return/refund flow");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = now;
        events.add(new OrderEvent.OrderCancelled(id, reason, reservationId));
    }

    public boolean isOwnedBy(String customerId) {
        return this.customerId.equals(customerId);
    }

    public boolean hasPromotion() {
        return promotionCode != null;
    }

    /** Lấy và xoá các event chưa phát; adapter persistence gọi khi lưu. */
    public List<OrderEvent> pullEvents() {
        List<OrderEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    private void requireStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw OrderException.conflict("cannot " + action + " order " + orderCode + " in status " + status);
        }
    }

    public String id() { return id; }
    public String orderCode() { return orderCode; }
    public String customerId() { return customerId; }
    public List<OrderLine> lines() { return lines; }
    public Money subtotal() { return subtotal; }
    public Money discount() { return discount; }
    public Money total() { return total; }
    public String promotionCode() { return promotionCode; }
    public CheckoutDetails checkout() { return checkout; }
    public Address shippingAddress() { return shippingAddress; }
    public Address billingAddress() { return billingAddress; }
    public String note() { return note; }
    public Instant placedAt() { return placedAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
    public OrderStatus status() { return status; }
    public String reservationId() { return reservationId; }
    public String paymentId() { return paymentId; }
    public String cancelReason() { return cancelReason; }

    /** Dựng lại từ storage (adapter persistence), không phát event. */
    public static Builder rehydrate() {
        return new Builder();
    }

    public static final class Builder {
        private String id, orderCode, customerId, promotionCode, note, reservationId, paymentId, cancelReason;
        private List<OrderLine> lines = List.of();
        private Money subtotal, discount, total;
        private CheckoutDetails checkout;
        private Address shippingAddress, billingAddress;
        private Instant placedAt, updatedAt;
        private OrderStatus status;
        private long version;

        public Builder id(String v) { id = v; return this; }
        public Builder orderCode(String v) { orderCode = v; return this; }
        public Builder customerId(String v) { customerId = v; return this; }
        public Builder lines(List<OrderLine> v) { lines = v; return this; }
        public Builder subtotal(Money v) { subtotal = v; return this; }
        public Builder discount(Money v) { discount = v; return this; }
        public Builder total(Money v) { total = v; return this; }
        public Builder promotionCode(String v) { promotionCode = v; return this; }
        public Builder checkout(CheckoutDetails v) { checkout = v; return this; }
        public Builder shippingAddress(Address v) { shippingAddress = v; return this; }
        public Builder billingAddress(Address v) { billingAddress = v; return this; }
        public Builder note(String v) { note = v; return this; }
        public Builder placedAt(Instant v) { placedAt = v; return this; }
        public Builder updatedAt(Instant v) { updatedAt = v; return this; }
        public Builder status(OrderStatus v) { status = v; return this; }
        public Builder reservationId(String v) { reservationId = v; return this; }
        public Builder paymentId(String v) { paymentId = v; return this; }
        public Builder cancelReason(String v) { cancelReason = v; return this; }
        public Builder version(long v) { version = v; return this; }

        public Order build() {
            return new Order(this);
        }
    }
}
