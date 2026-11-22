package com.congthanh.paymentservice.domain.model;

import com.congthanh.paymentservice.domain.event.PaymentEvent;
import com.congthanh.paymentservice.domain.exception.PaymentException;

import java.math.BigDecimal;
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
    private Instant authorizationExpiresAt;
    private Instant updatedAt;
    private BigDecimal refundedAmount;

    private final List<PaymentEvent> events = new ArrayList<>();

    private Payment(String id, String orderId, String customerId, Money amount, PaymentMethod method,
                    String provider, String providerRef, PaymentStatus status, String redirectUrl,
                    String failureReason, Instant authorizationExpiresAt, Instant createdAt, Instant updatedAt,
                    long version, BigDecimal refundedAmount) {
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
        this.authorizationExpiresAt = authorizationExpiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.refundedAmount = refundedAmount == null ? BigDecimal.ZERO : refundedAmount;
    }

    public static Payment initiate(String id, String orderId, String customerId, Money amount, PaymentMethod method,
                                   String provider, Instant now) {
        if (orderId == null || orderId.isBlank()) {
            throw PaymentException.invalid("orderId is required");
        }
        return new Payment(id, orderId, customerId, amount, method, provider, null, PaymentStatus.INITIATED,
                null, null, null, now, now, 0, BigDecimal.ZERO);
    }

    public static Payment rehydrate(String id, String orderId, String customerId, Money amount, PaymentMethod method,
                                    String provider, String providerRef, PaymentStatus status, String redirectUrl,
                                    String failureReason, Instant authorizationExpiresAt, Instant createdAt,
                                    Instant updatedAt, long version) {
        return rehydrate(id, orderId, customerId, amount, method, provider, providerRef, status, redirectUrl,
                failureReason, authorizationExpiresAt, createdAt, updatedAt, version, BigDecimal.ZERO);
    }

    public static Payment rehydrate(String id, String orderId, String customerId, Money amount, PaymentMethod method,
                                    String provider, String providerRef, PaymentStatus status, String redirectUrl,
                                    String failureReason, Instant authorizationExpiresAt, Instant createdAt,
                                    Instant updatedAt, long version, BigDecimal refundedAmount) {
        return new Payment(id, orderId, customerId, amount, method, provider, providerRef, status, redirectUrl,
                failureReason, authorizationExpiresAt, createdAt, updatedAt, version, refundedAmount);
    }

    public void authorized(String providerRef, Instant now) {
        authorized(providerRef, now, null);
    }

    public void authorized(String providerRef, Instant now, Instant expiresAt) {
        if (status == PaymentStatus.AUTHORIZED || status == PaymentStatus.CAPTURED) {
            return;
        }
        requireOneOf("authorize", PaymentStatus.INITIATED, PaymentStatus.PENDING_ACTION);
        this.providerRef = providerRef == null ? this.providerRef : providerRef;
        this.status = PaymentStatus.AUTHORIZED;
        this.authorizationExpiresAt = expiresAt;
        this.redirectUrl = null;
        this.updatedAt = now;
        events.add(new PaymentEvent.PaymentAuthorized(id, orderId, amount, provider, method.name()));
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
        events.add(new PaymentEvent.PaymentCaptured(id, orderId, customerId, amount, provider, method.name(), providerRef));
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
        BigDecimal remaining = amount.amount().subtract(refundedAmount);
        this.status = PaymentStatus.REFUNDED;
        this.refundedAmount = amount.amount();
        this.updatedAt = now;
        events.add(new PaymentEvent.PaymentRefunded(id, orderId, new Money(remaining, amount.currency()),
                "full-" + orderId, null, provider, true, providerRef));
    }

    /**
     * Kiểm tra trước khi gọi cổng: chỉ hoàn được tiền đã thu, cùng tiền tệ, và tổng không vượt số đã thu.
     * Tách khỏi {@link #partiallyRefunded} để không gọi cổng với yêu cầu chắc chắn sai.
     */
    public void checkRefundable(Money refund) {
        requireOneOf("refund", PaymentStatus.CAPTURED);
        if (!refund.currency().equals(amount.currency())) {
            throw PaymentException.invalid("refund currency " + refund.currency() + " does not match " + amount.currency());
        }
        if (refundedAmount.add(refund.amount()).compareTo(amount.amount()) > 0) {
            throw PaymentException.invalid("refund " + refund.amount() + " exceeds refundable "
                    + amount.amount().subtract(refundedAmount) + " for payment " + id);
        }
    }

    /** Một lần hoàn một phần đã được cổng chấp nhận. Hoàn đủ số đã thu thì chuyển REFUNDED. */
    public void partiallyRefunded(String refundId, Money refund, String reason, Instant now) {
        checkRefundable(refund);
        this.refundedAmount = refundedAmount.add(refund.amount());
        boolean full = refundedAmount.compareTo(amount.amount()) == 0;
        if (full) {
            this.status = PaymentStatus.REFUNDED;
        }
        this.updatedAt = now;
        events.add(new PaymentEvent.PaymentRefunded(id, orderId, refund, refundId, reason, provider, full, providerRef));
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
    public Instant authorizationExpiresAt() { return authorizationExpiresAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
    public BigDecimal refundedAmount() { return refundedAmount; }
}
