package com.congthanh.storedvalueservice.domain.model;

import com.congthanh.storedvalueservice.domain.exception.StoredValueException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Tài khoản giá trị lưu trữ của một khách (store credit; gift card dùng cùng mô hình ở bước sau).
 * {@code balance} là số dư thật, {@code held} là phần đang giữ cho đơn chưa xác nhận; khả dụng = balance − held.
 * Bất biến: 0 ≤ held ≤ balance. Mọi thay đổi sinh một {@link Movement} (lịch sử chỉ ghi thêm).
 */
public final class Account {

    public enum Kind { STORE_CREDIT, GIFT_CARD }

    public enum Status { ACTIVE, FROZEN }

    private final String id;
    private final String customerId;
    private final Kind kind;
    private final String currency;
    private final Instant createdAt;
    private final long version;

    private BigDecimal balance;
    private BigDecimal held;
    private Status status;
    private final List<Movement> movements = new ArrayList<>();

    private Account(String id, String customerId, Kind kind, String currency, BigDecimal balance, BigDecimal held,
                    Status status, Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.kind = Objects.requireNonNull(kind);
        this.currency = currency;
        this.balance = balance;
        this.held = held;
        this.status = status;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static Account open(String id, String customerId, Kind kind, String currency, Instant now) {
        if (customerId == null || customerId.isBlank()) {
            throw StoredValueException.invalid("customerId is required");
        }
        return new Account(id, customerId, kind, currency(currency), zero(), zero(), Status.ACTIVE, now, 0);
    }

    public static Account rehydrate(String id, String customerId, Kind kind, String currency, BigDecimal balance,
                                    BigDecimal held, Status status, Instant createdAt, long version) {
        return new Account(id, customerId, kind, currency, balance, held, status, createdAt, version);
    }

    /** Cộng tiền. Được phép cả khi tài khoản đang bị khoá (tiền hoàn của khách vẫn phải vào sổ). */
    public Movement credit(BigDecimal amount, String reason, String reference, Instant now) {
        BigDecimal a = positive(amount);
        requireText(reference, "reference");
        balance = balance.add(a);
        return record(Movement.Type.CREDIT, a, reason, reference, null, now);
    }

    /** Giữ chỗ cho đơn: không trừ hẳn, chỉ giảm số khả dụng. */
    public Movement hold(String holdId, String orderId, BigDecimal amount, Instant now) {
        BigDecimal a = positive(amount);
        if (status == Status.FROZEN) {
            throw StoredValueException.conflict("account " + id + " is frozen");
        }
        if (available().compareTo(a) < 0) {
            throw StoredValueException.insufficient("available " + available() + " " + currency + " is less than " + a);
        }
        held = held.add(a);
        return record(Movement.Type.HOLD, a, null, holdId, orderId, now);
    }

    /** Trừ hẳn phần đã giữ (đơn xác nhận). */
    public Movement capture(Hold hold, Instant now) {
        held = held.subtract(hold.amount());
        balance = balance.subtract(hold.amount());
        check();
        return record(Movement.Type.DEBIT, hold.amount(), null, hold.id(), hold.orderId(), now);
    }

    /** Trả lại phần đã giữ (saga bù trừ, hết hạn). */
    public Movement release(Hold hold, String reason, Instant now) {
        held = held.subtract(hold.amount());
        check();
        return record(Movement.Type.RELEASE, hold.amount(), reason, hold.id(), hold.orderId(), now);
    }

    public void freeze() {
        status = Status.FROZEN;
    }

    public void unfreeze() {
        status = Status.ACTIVE;
    }

    public BigDecimal available() {
        return balance.subtract(held);
    }

    public List<Movement> pullMovements() {
        List<Movement> out = List.copyOf(movements);
        movements.clear();
        return out;
    }

    private Movement record(Movement.Type type, BigDecimal amount, String reason, String reference, String orderId,
                            Instant now) {
        Movement m = new Movement(id, customerId, kind, type, amount, currency, reason, reference, orderId, balance, now);
        movements.add(m);
        return m;
    }

    private void check() {
        if (held.signum() < 0 || held.compareTo(balance) > 0) {
            throw StoredValueException.conflict("account " + id + " would break 0 <= held <= balance");
        }
    }

    private BigDecimal positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw StoredValueException.invalid("amount must be positive");
        }
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > digits) {
            throw StoredValueException.invalid("amount " + amount + " has more decimals than " + currency + " allows");
        }
        return amount.setScale(digits, RoundingMode.UNNECESSARY);
    }

    private static void requireText(String s, String name) {
        if (s == null || s.isBlank()) {
            throw StoredValueException.invalid(name + " is required");
        }
    }

    private static String currency(String c) {
        try {
            return Currency.getInstance(c == null ? "" : c.toUpperCase(java.util.Locale.ROOT)).getCurrencyCode();
        } catch (RuntimeException e) {
            throw StoredValueException.invalid("unknown currency " + c);
        }
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    public String id() { return id; }
    public String customerId() { return customerId; }
    public Kind kind() { return kind; }
    public String currency() { return currency; }
    public BigDecimal balance() { return balance; }
    public BigDecimal held() { return held; }
    public Status status() { return status; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }
}
