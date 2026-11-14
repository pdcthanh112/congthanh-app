package com.congthanh.returnsservice.domain.model;

import com.congthanh.returnsservice.domain.event.ReturnEvent;
import com.congthanh.returnsservice.domain.exception.ReturnException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ReturnRequest {

    private final String id;
    private final String orderId;
    private final String customerId;
    private final String rmaCode;
    private final String currency;
    private final RefundMethod refundMethod;
    private final Instant createdAt;
    private final long version;

    private List<ReturnLine> lines;
    private ReturnStatus status;
    private BigDecimal refundAmount;
    private BigDecimal restockingFee;
    private String paymentId;
    private String note;
    private Instant updatedAt;

    private final List<ReturnEvent> events = new ArrayList<>();

    private ReturnRequest(String id, String orderId, String customerId, String rmaCode, String currency,
                          RefundMethod refundMethod, List<ReturnLine> lines, ReturnStatus status, BigDecimal refundAmount,
                          BigDecimal restockingFee, String paymentId, String note, Instant createdAt,
                          Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.customerId = Objects.requireNonNull(customerId);
        this.rmaCode = Objects.requireNonNull(rmaCode);
        this.currency = currency;
        this.refundMethod = refundMethod == null ? RefundMethod.ORIGINAL_PAYMENT : refundMethod;
        this.lines = List.copyOf(lines);
        this.status = status;
        this.refundAmount = refundAmount;
        this.restockingFee = restockingFee;
        this.paymentId = paymentId;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static ReturnRequest open(String id, String rmaCode, OrderSnapshot order, String customerId,
                                     List<ReturnLine> requested, ReturnPolicy policy,
                                     Map<String, Integer> alreadyReturned, Instant now) {
        return open(id, rmaCode, order, customerId, requested, RefundMethod.ORIGINAL_PAYMENT, policy, alreadyReturned, now);
    }

    public static ReturnRequest open(String id, String rmaCode, OrderSnapshot order, String customerId,
                                     List<ReturnLine> requested, RefundMethod method, ReturnPolicy policy,
                                     Map<String, Integer> alreadyReturned, Instant now) {
        if (!order.customerId().equals(customerId)) {
            throw ReturnException.forbidden("order " + order.orderId() + " does not belong to this customer");
        }
        if (requested == null || requested.isEmpty()) {
            throw ReturnException.invalid("a return needs at least one line");
        }
        Set<String> seen = new HashSet<>();
        for (ReturnLine l : requested) {
            if (!seen.add(l.productId())) {
                throw ReturnException.invalid("product " + l.productId() + " appears twice; merge the lines");
            }
        }
        policy.checkEligible(order, requested, alreadyReturned, now);
        List<ReturnLine> priced = requested.stream()
                .map(l -> l.priced(order.line(l.productId()).orElseThrow().unitPrice()))
                .toList();
        ReturnRequest r = new ReturnRequest(id, order.orderId(), customerId, rmaCode, order.currency(), method, priced,
                ReturnStatus.REQUESTED, null, null, order.paymentId(), null, now, now, 0);
        r.events.add(new ReturnEvent.Requested(id, order.orderId(), customerId, rmaCode, priced));
        if (policy.autoApprove()) {
            r.approve("auto", now);
        }
        return r;
    }

    public static ReturnRequest rehydrate(String id, String orderId, String customerId, String rmaCode,
                                          String currency, RefundMethod refundMethod, List<ReturnLine> lines,
                                          ReturnStatus status, BigDecimal refundAmount, BigDecimal restockingFee,
                                          String paymentId, String note, Instant createdAt, Instant updatedAt,
                                          long version) {
        return new ReturnRequest(id, orderId, customerId, rmaCode, currency, refundMethod, lines, status, refundAmount,
                restockingFee, paymentId, note, createdAt, updatedAt, version);
    }

    public void approve(String by, Instant now) {
        if (status == ReturnStatus.APPROVED) {
            return;
        }
        require("approve", ReturnStatus.REQUESTED);
        status = ReturnStatus.APPROVED;
        updatedAt = now;
        events.add(new ReturnEvent.Approved(id, orderId, customerId, rmaCode, by));
    }

    public void reject(String reason, Instant now) {
        if (status == ReturnStatus.REJECTED) {
            return;
        }
        require("reject", ReturnStatus.REQUESTED, ReturnStatus.APPROVED, ReturnStatus.RECEIVED);
        if (reason == null || reason.isBlank()) {
            throw ReturnException.invalid("a rejection needs a reason");
        }
        status = ReturnStatus.REJECTED;
        note = reason;
        updatedAt = now;
        events.add(new ReturnEvent.Rejected(id, orderId, customerId, reason));
        events.add(new ReturnEvent.Closed(id, orderId, "REJECTED"));
    }

    public void cancel(String byCustomer, Instant now) {
        if (status == ReturnStatus.CANCELLED) {
            return;
        }
        if (!customerId.equals(byCustomer)) {
            throw ReturnException.forbidden("only the customer who opened return " + id + " can cancel it");
        }
        require("cancel", ReturnStatus.REQUESTED, ReturnStatus.APPROVED);
        status = ReturnStatus.CANCELLED;
        updatedAt = now;
        events.add(new ReturnEvent.Closed(id, orderId, "CANCELLED"));
    }

    public boolean receive(Map<String, ItemCondition> conditions, ReturnPolicy policy, BigDecimal refundableLeft,
                           Instant now) {
        if (status == ReturnStatus.RECEIVED || status == ReturnStatus.REFUND_PENDING) {
            return true;
        }
        require("receive", ReturnStatus.APPROVED);
        List<ReturnLine> inspected = new ArrayList<>();
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        for (ReturnLine l : lines) {
            ItemCondition c = conditions.get(l.productId());
            if (c == null) {
                throw ReturnException.invalid("missing inspection result for product " + l.productId());
            }
            inspected.add(l.inspected(c));
            boolean refundable = c == ItemCondition.PASSED || (c == ItemCondition.DAMAGED && l.reason().sellerFault());
            if (refundable) {
                BigDecimal fee = c == ItemCondition.PASSED ? policy.restockingFee(l.reason(), l.value()) : BigDecimal.ZERO;
                refund = refund.add(l.value()).subtract(fee);
                fees = fees.add(fee);
            }
        }
        lines = List.copyOf(inspected);
        refund = refund.min(refundableLeft.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
        refundAmount = refund;
        restockingFee = fees.setScale(2, RoundingMode.HALF_UP);
        updatedAt = now;
        events.add(new ReturnEvent.Received(id, orderId, lines));
        if (refund.signum() <= 0) {
            status = ReturnStatus.RECEIVED;
            reject("INSPECTION_FAILED", now);
            return false;
        }
        status = ReturnStatus.REFUND_PENDING;
        return true;
    }

    public void refunded(String paymentId, Instant now) {
        if (status == ReturnStatus.REFUNDED) {
            return;
        }
        require("mark refunded", ReturnStatus.REFUND_PENDING, ReturnStatus.REFUND_FAILED);
        this.paymentId = paymentId != null ? paymentId : this.paymentId;
        status = ReturnStatus.REFUNDED;
        updatedAt = now;
        events.add(new ReturnEvent.RefundIssued(id, orderId, customerId, refundAmount, restockingFee, currency,
                this.paymentId, refundMethod));
        events.add(new ReturnEvent.Closed(id, orderId, "REFUNDED"));
    }

    public void refundFailed(String reason, Instant now) {
        if (status == ReturnStatus.REFUND_FAILED) {
            return;
        }
        require("mark refund failed", ReturnStatus.REFUND_PENDING);
        status = ReturnStatus.REFUND_FAILED;
        note = reason;
        updatedAt = now;
    }

    public void retryRefund(Instant now) {
        require("retry refund", ReturnStatus.REFUND_FAILED);
        status = ReturnStatus.REFUND_PENDING;
        updatedAt = now;
    }

    public List<ReturnEvent> pullEvents() {
        List<ReturnEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    private void require(String action, ReturnStatus... allowed) {
        for (ReturnStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw ReturnException.conflict("cannot " + action + " return " + id + " in status " + status);
    }

    public String id() { return id; }
    public String orderId() { return orderId; }
    public String customerId() { return customerId; }
    public String rmaCode() { return rmaCode; }
    public String currency() { return currency; }
    public RefundMethod refundMethod() { return refundMethod; }
    public List<ReturnLine> lines() { return lines; }
    public ReturnStatus status() { return status; }
    public BigDecimal refundAmount() { return refundAmount; }
    public BigDecimal restockingFee() { return restockingFee; }
    public String paymentId() { return paymentId; }
    public String note() { return note; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
