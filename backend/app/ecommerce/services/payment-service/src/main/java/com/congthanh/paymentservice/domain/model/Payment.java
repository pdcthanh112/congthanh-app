package com.congthanh.paymentservice.domain.model;

import com.congthanh.paymentservice.domain.event.PaymentEvent;
import com.congthanh.paymentservice.domain.exception.PaymentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Một lần thanh toán cho một đơn (order_id unique). Chuyển trạng thái idempotent vì activity
 * Temporal và webhook của cổng thanh toán đều có thể đến nhiều lần.
 */
public final class Payment {

    private final String id;
    private final String orderId;
    private final String customerId;
    private final Money amount;
    private final PaymentMethod method;
    private final Instant createdAt;
    private final long version;

    private String provider;
    private String providerRef;
    private PaymentStatus status;
    private String redirectUrl;
    private String failureReason;
    private Instant updatedAt;

    private final List<PaymentEvent> events = new ArrayList<>();

    private Payment(String id, String orderId, String customerId, Money amount, PaymentMethod method,
                    String provider, String providerRef, PaymentStatus status, String redirectUrl,
                    String failureReason, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.customerId = customerId;
        this.amount = Objects.requireNonNull(amount);
        this.method = Objects.requireNonNull(method);
        this.provider = provider;
        this.providerRef = providerRef;
        this.status = status;
        this.redirectUrl = redirectUrl;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Payment initiate(String id, String orderId, String customerId, Money amount, PaymentMethod method,
                                   String provider, Instant now) {
        if (orderId == null || orderId.isBlank()) {
            throw PaymentException.invalid("orderId is required");
        }
        return new Payment(id, orderId, customerId, amount, method, provider, null, PaymentStatus.INITIATED,
                null, null, now, now, 0);
    }

    public static Payment rehydrate(String id, String orderId, String customerId, Money amount, PaymentMethod method,
                                    String provider, String providerRef, PaymentStatus status, String redirectUrl,
                                    String failureReason, Instant createdAt, Instant updatedAt, long version) {
        return new Payment(id, orderId, customerId, amount, method, provider, providerRef, status, redirectUrl,
                failureReason, createdAt, updatedAt, version);
    }

    public void authorized(String providerRef, Instant now) {
        if (status == PaymentStatus.AUTHORIZED || status == PaymentStatus.CAPTURED) {
            return;
        }
        requireOneOf("authorize", PaymentStatus.INITIATED, PaymentStatus.PENDING_ACTION);
        this.providerRef = providerRef == null ? this.providerRef : providerRef;
        this.status = PaymentStatus.AUTHORIZED;
        this.redirectUrl = null;
        this.updatedAt = now;
        events.add(new PaymentEvent.PaymentAuthorized(id, orderId, amount, provider));
    }

    /** Cổng kiểu "sale" (VNPay, MoMo): tiền đã trừ ngay khi khách trả, không có bước capture riêng. */
    public void paidImmediately(String providerRef, Instant now) {
        if (status == PaymentStatus.CAPTURED) {
            return;
        }
        authorized(providerRef, now);
        this.status = PaymentStatus.CAPTURED;
    }

    public void actionRequired(String providerRef, String redirectUrl, Instant now) {
        if (status == PaymentStatus.PENDING_ACTION) {
            return;
        }
        requireOneOf("request customer action", PaymentStatus.INITIATED);
        this.providerRef = providerRef;
        this.redirectUrl = redirectUrl;
        this.status = PaymentStatus.PENDING_ACTION;
        this.updatedAt = now;
    }

    public void declined(String reason, Instant now) {
        if (status == PaymentStatus.DECLINED) {
            return;
        }
        requireOneOf("decline", PaymentStatus.INITIATED, PaymentStatus.PENDING_ACTION);
        this.status = PaymentStatus.DECLINED;
        this.failureReason = reason;
        this.redirectUrl = null;
        this.updatedAt = now;
        events.add(new PaymentEvent.PaymentFailed(orderId, reason));
    }

    public void captured(Instant now) {
        if (status == PaymentStatus.CAPTURED) {
            return;
        }
        requireOneOf("capture", PaymentStatus.AUTHORIZED);
        this.status = PaymentStatus.CAPTURED;
        this.updatedAt = now;
    }

    /** Huỷ khi chưa thu tiền (authorization được giải phóng, hoặc khách chưa trả ở cổng). */
    public void voided(String reason, Instant now) {
        if (status == PaymentStatus.VOIDED) {
            return;
        }
        requireOneOf("void", PaymentStatus.INITIATED, PaymentStatus.PENDING_ACTION, PaymentStatus.AUTHORIZED);
        this.status = PaymentStatus.VOIDED;
        this.failureReason = reason;
        this.redirectUrl = null;
        this.updatedAt = now;
    }

    public void refunded(Instant now) {
        if (status == PaymentStatus.REFUNDED) {
            return;
        }
        requireOneOf("refund", PaymentStatus.CAPTURED, PaymentStatus.VOIDED);
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = now;
        events.add(new PaymentEvent.PaymentRefunded(id, orderId, amount));
    }

    public List<PaymentEvent> pullEvents() {
        List<PaymentEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    private void requireOneOf(String action, PaymentStatus... allowed) {
        for (PaymentStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw PaymentException.conflict("cannot " + action + " payment " + id + " in status " + status);
    }

    public String id() { return id; }
    public String orderId() { return orderId; }
    public String customerId() { return customerId; }
    public Money amount() { return amount; }
    public PaymentMethod method() { return method; }
    public String provider() { return provider; }
    public String providerRef() { return providerRef; }
    public PaymentStatus status() { return status; }
    public String redirectUrl() { return redirectUrl; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
