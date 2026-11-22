package com.congthanh.loyaltyservice.domain.model;

import com.congthanh.loyaltyservice.domain.event.LoyaltyEvent;
import com.congthanh.loyaltyservice.domain.exception.LoyaltyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Member {

    public record Rules(Duration returnWindow, Duration fallbackConfirm, Duration lotLifetime, String currency,
                        BigDecimal pointValue, long minRedeem) {
        public Rules {
            java.util.Objects.requireNonNull(currency, "currency");
            java.util.Objects.requireNonNull(pointValue, "pointValue");
        }
    }

    public record Allocation(String lotId, long points) {
    }

    private final String customerId;
    private final long version;
    private Tier tier;
    private final Map<String, Lot> lots = new LinkedHashMap<>();
    private final List<LoyaltyEvent> events = new ArrayList<>();

    public Member(String customerId, Tier tier, List<Lot> lots, long version) {
        this.customerId = Objects.requireNonNull(customerId);
        this.tier = tier == null ? Tier.MEMBER : tier;
        this.version = version;
        lots.forEach(l -> this.lots.put(l.id(), l));
    }

    public Lot earn(String lotId, String orderId, BigDecimal orderTotal, Instant now) {
        if (lots.values().stream().anyMatch(l -> l.orderId().equals(orderId))) {
            return lots.values().stream().filter(l -> l.orderId().equals(orderId)).findFirst().orElseThrow();
        }
        long points = orderTotal.multiply(BigDecimal.valueOf(tier.earnPercent()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact();
        Lot lot = new Lot(lotId, customerId, orderId, points, points, Lot.Status.PENDING, orderTotal, now, null, null);
        if (points > 0) {
            lots.put(lotId, lot);
            events.add(new LoyaltyEvent.Earned(customerId, orderId, lotId, points));
        }
        return lot;
    }

    public void delivered(String orderId, Instant deliveredAt, Rules rules) {
        lots.replaceAll((id, l) -> l.orderId().equals(orderId) && l.status() == Lot.Status.PENDING && l.confirmAt() == null
                ? l.withConfirmAt(deliveredAt.plus(rules.returnWindow())) : l);
    }

    public void confirmDue(Instant now, Rules rules) {
        for (Lot l : List.copyOf(lots.values())) {
            if (l.status() != Lot.Status.PENDING || l.remaining() == 0) {
                continue;
            }
            Instant due = l.confirmAt() != null ? l.confirmAt() : l.earnedAt().plus(rules.fallbackConfirm());
            if (!now.isBefore(due)) {
                lots.put(l.id(), l.withStatus(Lot.Status.AVAILABLE, now.plus(rules.lotLifetime())));
                events.add(new LoyaltyEvent.Confirmed(customerId, l.id(), l.orderId(), l.remaining()));
            }
        }
    }

    public void expireDue(Instant now) {
        for (Lot l : List.copyOf(lots.values())) {
            if (l.status() == Lot.Status.AVAILABLE && l.expiresAt() != null && now.isAfter(l.expiresAt()) && l.remaining() > 0) {
                events.add(new LoyaltyEvent.Expired(customerId, l.id(), l.remaining()));
                lots.put(l.id(), l.withRemaining(0).withStatus(Lot.Status.EXPIRED, l.expiresAt()));
            }
        }
    }

    public long reverse(String orderId, BigDecimal refunded, String reason) {
        long total = 0;
        for (Lot l : List.copyOf(lots.values())) {
            if (!l.orderId().equals(orderId) || l.remaining() == 0
                    || (l.status() != Lot.Status.PENDING && l.status() != Lot.Status.AVAILABLE)) {
                continue;
            }
            long target = l.orderTotal().signum() == 0 ? l.points()
                    : refunded.multiply(BigDecimal.valueOf(l.points()))
                    .divide(l.orderTotal(), 0, RoundingMode.CEILING).longValue();
            long take = Math.min(Math.max(target, 0), l.remaining());
            if (take == 0) {
                continue;
            }
            boolean confirmed = l.status() == Lot.Status.AVAILABLE;
            lots.put(l.id(), l.withRemaining(l.remaining() - take));
            events.add(new LoyaltyEvent.Reversed(customerId, orderId, take, confirmed, reason));
            total += take;
        }
        return total;
    }

    public long available() {
        return lots.values().stream().filter(l -> l.status() == Lot.Status.AVAILABLE).mapToLong(Lot::remaining).sum();
    }

    public long pending() {
        return lots.values().stream().filter(l -> l.status() == Lot.Status.PENDING).mapToLong(Lot::remaining).sum();
    }

    public List<Allocation> reserve(long points, Rules rules) {
        if (points < rules.minRedeem()) {
            throw LoyaltyException.invalid("redeem at least " + rules.minRedeem() + " points");
        }
        if (available() < points) {
            throw LoyaltyException.insufficient("only " + available() + " points available");
        }
        List<Allocation> out = new ArrayList<>();
        long left = points;
        List<Lot> usable = lots.values().stream().filter(l -> l.status() == Lot.Status.AVAILABLE && l.remaining() > 0)
                .sorted(Comparator.comparing(Lot::expiresAt)).toList();
        for (Lot l : usable) {
            long take = Math.min(left, l.remaining());
            lots.put(l.id(), l.withRemaining(l.remaining() - take));
            out.add(new Allocation(l.id(), take));
            left -= take;
            if (left == 0) {
                break;
            }
        }
        return out;
    }

    public void release(List<Allocation> allocations, Instant now) {
        for (Allocation a : allocations) {
            Lot l = lots.get(a.lotId());
            if (l == null) {
                continue;
            }
            Lot back = l.withRemaining(l.remaining() + a.points());
            if (l.status() == Lot.Status.EXPIRED || (l.expiresAt() != null && now.isAfter(l.expiresAt()))) {
                events.add(new LoyaltyEvent.Expired(customerId, l.id(), a.points()));
                back = l;
            }
            lots.put(l.id(), back);
        }
    }

    public void redeemed(String redemptionId, String orderId, long points) {
        events.add(new LoyaltyEvent.Redeemed(customerId, redemptionId, orderId, points));
    }

    public BigDecimal spend12m(Instant now) {
        Instant from = now.minus(Duration.ofDays(365));
        return lots.values().stream()
                .filter(l -> (l.status() == Lot.Status.AVAILABLE || l.status() == Lot.Status.EXPIRED) && l.earnedAt().isAfter(from))
                .map(Lot::orderTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void retier(Instant now) {
        Tier next = Tier.forSpend(spend12m(now));
        if (next != tier) {
            events.add(new LoyaltyEvent.TierChanged(customerId, tier, next));
            tier = next;
        }
    }

    public BigDecimal value(long points, Rules rules) {
        int digits = Math.max(0, java.util.Currency.getInstance(rules.currency()).getDefaultFractionDigits());
        return rules.pointValue().multiply(BigDecimal.valueOf(points)).setScale(digits, RoundingMode.HALF_UP);
    }

    public List<LoyaltyEvent> pullEvents() {
        List<LoyaltyEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    public String customerId() { return customerId; }
    public Tier tier() { return tier; }
    public long version() { return version; }
    public List<Lot> lots() { return List.copyOf(lots.values()); }
}
