package com.congthanh.promotionservice.domain.model;

import com.congthanh.promotionservice.domain.event.PromotionEvent;
import com.congthanh.promotionservice.domain.exception.PromotionException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Voucher. Bất biến quan trọng: usedCount ≤ usageLimit, kể cả khi nhiều checkout redeem cùng lúc
 * (repository khoá dòng khi redeem).
 */
public final class Promotion {

    private final Long id;
    private String code;
    private PromotionType type;
    private DiscountRule rule;
    private int usageLimit;          // 0 = không giới hạn
    private int perCustomerLimit;    // 0 = không giới hạn
    private int usedCount;
    private Instant startsAt;
    private Instant endsAt;
    private boolean active;
    private String description;
    private final long version;
    private final List<PromotionEvent> events = new ArrayList<>();

    private Promotion(Long id, String code, PromotionType type, DiscountRule rule, int usageLimit, int perCustomerLimit,
                      int usedCount, Instant startsAt, Instant endsAt, boolean active, String description, long version) {
        this.id = id;
        this.code = normalize(code);
        this.type = type;
        this.rule = rule;
        this.usageLimit = usageLimit;
        this.perCustomerLimit = perCustomerLimit;
        this.usedCount = usedCount;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
        this.description = description;
        this.version = version;
    }

    public static Promotion create(String code, PromotionType type, DiscountRule rule, int usageLimit,
                                   int perCustomerLimit, Instant startsAt, Instant endsAt, String description) {
        validate(code, type, rule, usageLimit, perCustomerLimit, startsAt, endsAt);
        return new Promotion(null, code, type, rule, usageLimit, perCustomerLimit, 0, startsAt, endsAt, true, description, 0);
    }

    public static Promotion rehydrate(Long id, String code, PromotionType type, DiscountRule rule, int usageLimit,
                                      int perCustomerLimit, int usedCount, Instant startsAt, Instant endsAt,
                                      boolean active, String description, long version) {
        return new Promotion(id, code, type, rule, usageLimit, perCustomerLimit, usedCount, startsAt, endsAt, active,
                description, version);
    }

    public void update(PromotionType type, DiscountRule rule, int usageLimit, int perCustomerLimit,
                       Instant startsAt, Instant endsAt, String description) {
        validate(code, type, rule, usageLimit, perCustomerLimit, startsAt, endsAt);
        if (usageLimit > 0 && usageLimit < usedCount) {
            throw PromotionException.conflict("usage limit cannot be below the " + usedCount + " uses already made");
        }
        this.type = type;
        this.rule = rule;
        this.usageLimit = usageLimit;
        this.perCustomerLimit = perCustomerLimit;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.description = description;
    }

    public void deactivate() {
        if (active) {
            active = false;
            events.add(new PromotionEvent.PromotionExpired(id));
        }
    }

    /** Ghi event kích hoạt sau khi đã có id (gọi bởi repository lúc tạo). */
    public void announceActivated(Long assignedId) {
        events.add(new PromotionEvent.PromotionActivated(assignedId, code, startsAt, endsAt));
    }

    /** @param customerUses số lần khách này đã dùng (redemption còn hiệu lực) */
    public Quote quote(BigDecimal subtotal, String currency, int customerUses, Instant now) {
        if (!active) {
            return Quote.rejected("INACTIVE", currency);
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return Quote.rejected("NOT_STARTED", currency);
        }
        if (endsAt != null && !now.isBefore(endsAt)) {
            return Quote.rejected("EXPIRED", currency);
        }
        if (usageLimit > 0 && usedCount >= usageLimit) {
            return Quote.rejected("EXHAUSTED", currency);
        }
        if (perCustomerLimit > 0 && customerUses >= perCustomerLimit) {
            return Quote.rejected("ALREADY_USED", currency);
        }
        if (!rule.currency().equals(currency)) {
            return Quote.rejected("CURRENCY", currency);
        }
        if (subtotal.compareTo(rule.minSubtotal()) < 0) {
            return Quote.rejected("MIN_ORDER", currency);
        }
        return new Quote(true, rule.discountFor(subtotal), currency, null, id);
    }

    /** Dùng một lượt; caller phải giữ khoá dòng. Trả về quote đã áp dụng. */
    public Quote redeem(BigDecimal subtotal, String currency, int customerUses, String orderId, String customerId, Instant now) {
        Quote q = quote(subtotal, currency, customerUses, now);
        if (q.valid()) {
            usedCount++;
            events.add(new PromotionEvent.PromotionRedeemed(id, code, orderId, customerId));
        }
        return q;
    }

    public void releaseUse(String orderId, String reason) {
        if (usedCount > 0) {
            usedCount--;
        }
        events.add(new PromotionEvent.RedemptionCancelled(id, orderId, reason));
    }

    public List<PromotionEvent> pullEvents() {
        List<PromotionEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    private static void validate(String code, PromotionType type, DiscountRule rule, int usageLimit,
                                 int perCustomerLimit, Instant startsAt, Instant endsAt) {
        String c = normalize(code);
        if (c == null || !c.matches("[A-Z0-9_-]{3,32}")) {
            throw PromotionException.invalid("code must be 3-32 characters of A-Z, 0-9, _ or -");
        }
        if (type == null || rule == null) {
            throw PromotionException.invalid("type and discount rule are required");
        }
        if (usageLimit < 0 || perCustomerLimit < 0) {
            throw PromotionException.invalid("limits must not be negative");
        }
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw PromotionException.invalid("endsAt must be after startsAt");
        }
    }

    static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public Long id() { return id; }
    public String code() { return code; }
    public PromotionType type() { return type; }
    public DiscountRule rule() { return rule; }
    public int usageLimit() { return usageLimit; }
    public int perCustomerLimit() { return perCustomerLimit; }
    public int usedCount() { return usedCount; }
    public Instant startsAt() { return startsAt; }
    public Instant endsAt() { return endsAt; }
    public boolean active() { return active; }
    public String description() { return description; }
    public long version() { return version; }
}
