package com.congthanh.subscriptionservice.domain.model;

import com.congthanh.subscriptionservice.domain.event.SubscriptionEvent;
import com.congthanh.subscriptionservice.domain.exception.SubscriptionException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class Subscription {

    public record Schedule(Duration reminderLead, Duration orderLead, List<Duration> retryBackoff) {
        public static Schedule defaults() {
            return new Schedule(Duration.ofDays(3), Duration.ofDays(2),
                    List.of(Duration.ofDays(1), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2)));
        }
    }

    private final String id;
    private final String customerId;
    private final String paymentMethodRef;
    private final String addressId;
    private final Instant createdAt;
    private final long version;

    private List<Line> lines;
    private int intervalDays;
    private LocalDate nextDeliveryDate;
    private SubscriptionStatus status;
    private int cycle;
    private int attempts;
    private Instant nextAttemptAt;
    private int remindedCycle;
    private LocalDate pausedUntil;
    private String cancelReason;
    private final List<SubscriptionEvent> events = new ArrayList<>();

    private Subscription(String id, String customerId, List<Line> lines, int intervalDays, LocalDate nextDeliveryDate,
                         String paymentMethodRef, String addressId, SubscriptionStatus status, int cycle, int attempts,
                         Instant nextAttemptAt, int remindedCycle, LocalDate pausedUntil, String cancelReason,
                         Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.lines = List.copyOf(lines);
        this.intervalDays = intervalDays;
        this.nextDeliveryDate = nextDeliveryDate;
        this.paymentMethodRef = paymentMethodRef;
        this.addressId = addressId;
        this.status = status;
        this.cycle = cycle;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.remindedCycle = remindedCycle;
        this.pausedUntil = pausedUntil;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static Subscription create(String id, String customerId, List<Line> lines, int intervalDays,
                                      LocalDate firstDelivery, String paymentMethodRef, String addressId,
                                      LocalDate today, Instant now) {
        validate(lines, intervalDays);
        if (firstDelivery == null || !firstDelivery.isAfter(today.plusDays(2))) {
            throw SubscriptionException.invalid("first delivery must be at least 3 days from today");
        }
        if (paymentMethodRef == null || paymentMethodRef.isBlank()) {
            throw SubscriptionException.invalid("a saved payment method is required");
        }
        Subscription s = new Subscription(id, customerId, lines, intervalDays, firstDelivery, paymentMethodRef,
                addressId, SubscriptionStatus.ACTIVE, 1, 0, null, 0, null, null, now, 0);
        s.events.add(new SubscriptionEvent.Created(s));
        return s;
    }

    public static Subscription rehydrate(String id, String customerId, List<Line> lines, int intervalDays,
                                         LocalDate nextDeliveryDate, String paymentMethodRef, String addressId,
                                         SubscriptionStatus status, int cycle, int attempts, Instant nextAttemptAt,
                                         int remindedCycle, LocalDate pausedUntil, String cancelReason,
                                         Instant createdAt, long version) {
        return new Subscription(id, customerId, lines, intervalDays, nextDeliveryDate, paymentMethodRef, addressId,
                status, cycle, attempts, nextAttemptAt, remindedCycle, pausedUntil, cancelReason, createdAt, version);
    }

    private static void validate(List<Line> lines, int intervalDays) {
        if (lines == null || lines.isEmpty()) {
            throw SubscriptionException.invalid("a subscription needs at least one product");
        }
        if (new HashSet<>(lines.stream().map(Line::productId).toList()).size() != lines.size()) {
            throw SubscriptionException.invalid("each product may appear once");
        }
        if (intervalDays < 7 || intervalDays > 90) {
            throw SubscriptionException.invalid("interval must be within 7..90 days");
        }
    }

    public void requireOwner(String caller) {
        if (!customerId.equals(caller)) {
            throw SubscriptionException.notFound("subscription " + id);
        }
    }

    public void change(List<Line> newLines, Integer newInterval) {
        requireNotCancelled("change");
        List<Line> l = newLines == null ? lines : newLines;
        int i = newInterval == null ? intervalDays : newInterval;
        validate(l, i);
        lines = List.copyOf(l);
        intervalDays = i;
    }

    public void skipNext() {
        requireNotCancelled("skip");
        nextDeliveryDate = nextDeliveryDate.plusDays(intervalDays);
        remindedCycle = Math.min(remindedCycle, cycle - 1);
    }

    public void pause(LocalDate until, LocalDate today) {
        requireNotCancelled("pause");
        if (until != null && !until.isAfter(today)) {
            throw SubscriptionException.invalid("pause end must be in the future");
        }
        pausedUntil = until;
        transition(SubscriptionStatus.PAUSED, "CUSTOMER_PAUSED");
    }

    public void resume(LocalDate today) {
        if (status != SubscriptionStatus.PAUSED) {
            throw SubscriptionException.conflict("subscription " + id + " is not paused");
        }
        pausedUntil = null;
        while (!nextDeliveryDate.isAfter(today.plusDays(2))) {
            nextDeliveryDate = nextDeliveryDate.plusDays(intervalDays);
        }
        transition(SubscriptionStatus.ACTIVE, "CUSTOMER_RESUMED");
    }

    public void cancel(String reason) {
        if (status == SubscriptionStatus.CANCELLED) {
            return;
        }
        cancelReason = reason;
        transition(SubscriptionStatus.CANCELLED, reason);
    }

    public boolean reminderDue(LocalDate today, Schedule schedule) {
        return status == SubscriptionStatus.ACTIVE && remindedCycle < cycle
                && !today.plusDays(schedule.reminderLead().toDays()).isBefore(nextDeliveryDate);
    }

    public void reminded() {
        remindedCycle = cycle;
        events.add(new SubscriptionEvent.RenewalUpcoming(this));
    }

    public boolean renewalDue(LocalDate today, Instant now, Schedule schedule) {
        if (status == SubscriptionStatus.PAUSED && pausedUntil != null && !today.isBefore(pausedUntil)) {
            resume(today);
        }
        return switch (status) {
            case ACTIVE -> !today.plusDays(schedule.orderLead().toDays()).isBefore(nextDeliveryDate);
            case PAST_DUE -> nextAttemptAt != null && !now.isBefore(nextAttemptAt);
            default -> false;
        };
    }

    public String renewalKey() {
        return id + ":" + cycle + ":" + attempts;
    }

    public void renewed(String orderId) {
        events.add(new SubscriptionEvent.OrderPlaced(this, cycle, orderId));
        cycle++;
        attempts = 0;
        nextAttemptAt = null;
        nextDeliveryDate = nextDeliveryDate.plusDays(intervalDays);
        if (status == SubscriptionStatus.PAST_DUE) {
            transition(SubscriptionStatus.ACTIVE, "RENEWAL_SUCCEEDED");
        }
    }

    /**
     * Đơn của kỳ {@code orderCycle} bị huỷ sau khi đặt (thẻ bị từ chối trong saga, hết hàng lúc giữ kho): quay lại kỳ đó
     * rồi tính như một lần gia hạn thất bại. Bỏ qua nếu đó không phải kỳ vừa gia hạn hoặc gói đã huỷ.
     */
    public void renewalOrderCancelled(int orderCycle, String reason, Instant now, Schedule schedule) {
        if (status == SubscriptionStatus.CANCELLED || orderCycle != cycle - 1) {
            return;
        }
        cycle = orderCycle;
        nextDeliveryDate = nextDeliveryDate.minusDays(intervalDays);
        renewalFailed(reason, now, schedule);
    }

    public void renewalFailed(String reason, Instant now, Schedule schedule) {
        attempts++;
        if (attempts > schedule.retryBackoff().size()) {
            events.add(new SubscriptionEvent.RenewalFailed(this, cycle, attempts, reason, null));
            cancel("RENEWAL_FAILED: " + reason);
            return;
        }
        nextAttemptAt = now.plus(schedule.retryBackoff().get(attempts - 1));
        events.add(new SubscriptionEvent.RenewalFailed(this, cycle, attempts, reason, nextAttemptAt));
        if (status != SubscriptionStatus.PAST_DUE) {
            transition(SubscriptionStatus.PAST_DUE, reason);
        }
    }

    private void transition(SubscriptionStatus to, String reason) {
        SubscriptionStatus from = status;
        if (from == to) {
            return;
        }
        status = to;
        events.add(new SubscriptionEvent.StatusChanged(this, from, to, reason));
    }

    private void requireNotCancelled(String action) {
        if (status == SubscriptionStatus.CANCELLED) {
            throw SubscriptionException.conflict("cannot " + action + " a cancelled subscription");
        }
    }

    public List<SubscriptionEvent> pullEvents() {
        List<SubscriptionEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    public String id() { return id; }
    public String customerId() { return customerId; }
    public List<Line> lines() { return lines; }
    public int intervalDays() { return intervalDays; }
    public LocalDate nextDeliveryDate() { return nextDeliveryDate; }
    public String paymentMethodRef() { return paymentMethodRef; }
    public String addressId() { return addressId; }
    public SubscriptionStatus status() { return status; }
    public int cycle() { return cycle; }
    public int attempts() { return attempts; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public int remindedCycle() { return remindedCycle; }
    public LocalDate pausedUntil() { return pausedUntil; }
    public String cancelReason() { return cancelReason; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }
}
