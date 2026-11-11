package com.congthanh.checkoutservice.domain.model;

import com.congthanh.checkoutservice.domain.exception.CheckoutException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate phiên checkout: giữ giỏ hàng đã chốt, báo giá mới nhất (giá, giảm giá, phí giao, thuế) và kết quả
 * kiểm tra gian lận. Order id được cấp ngay khi mở phiên nên tạo đơn ở order-service là idempotent.
 */
public final class CheckoutSession {

    public static final int MAX_LINES = 100;
    public static final int MAX_QUANTITY = 999;

    public record Item(String productId, int quantity) {
    }

    public record PricedLine(String productId, String sku, String name, int quantity, Money unitPrice, Money compareAt,
                             Money lineTotal, int weightGrams, long taxClassId) {
    }

    public record ShippingOption(String carrier, String serviceCode, String serviceName, Money fee, int daysMin,
                                 int daysMax) {
        public String key() {
            return carrier + ":" + serviceCode;
        }
    }

    public record Totals(Money subtotal, Money discount, Money shippingFee, Money tax, Money total) {
        static Totals zero(String currency) {
            Money z = Money.zero(currency);
            return new Totals(z, z, z, z, z);
        }
    }

    public record Payment(String method, String token, String returnUrl, String clientIp, String deviceId) {
    }

    public record FraudVerdict(String assessmentId, String decision, int score, List<String> reasons) {
    }

    /**
     * Suất flash sale đã giành ở inventory-service. Phiên có field này chỉ chứa đúng sản phẩm và số lượng đã giành,
     * hết hạn cùng lúc với claim, và dùng claim id làm id phiên lẫn order id (inventory-service nhận ra đơn flash
     * sale qua order id để xác nhận claim thay vì giữ thêm hàng).
     */
    public record FlashSale(String saleId, String productId, String claimId, Instant expiresAt) {
    }

    /** Kết quả báo giá các dịch vụ khác trả về, dùng trong {@link #applyQuote}. */
    public record Quote(List<PricedLine> lines, List<String> unpricedProductIds, Money discount, String promotionError,
                        List<ShippingOption> shippingOptions, Map<String, Money> taxByLine) {
    }

    private final String id;
    private final String orderId;
    private final String customerId;
    private final String currency;
    private final List<String> customerGroups;
    private final Instant createdAt;
    private final long version;

    private SessionStatus status;
    private List<Item> items;
    private List<PricedLine> lines = List.of();
    private List<String> unpriced = List.of();
    private String promotionCode;
    private String promotionError;
    private Address shippingAddress;
    private Address billingAddress;
    private String note;
    private List<ShippingOption> shippingOptions = List.of();
    private String selectedShipping;
    private Totals totals;
    private Payment payment;
    private FraudVerdict fraud;
    private String orderCode;
    private String failureReason;
    private Instant updatedAt;
    private Instant expiresAt;
    private FlashSale flashSale;

    private CheckoutSession(String id, String orderId, String customerId, String currency, List<String> customerGroups,
                            Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.customerId = Objects.requireNonNull(customerId);
        this.currency = Objects.requireNonNull(currency);
        this.customerGroups = List.copyOf(customerGroups);
        this.createdAt = createdAt;
        this.version = version;
    }

    public static CheckoutSession open(String id, String orderId, String customerId, List<String> groups, String currency,
                                       List<Item> items, Instant now, Duration ttl) {
        if (customerId == null || customerId.isBlank()) {
            throw CheckoutException.invalid("customerId is required");
        }
        CheckoutSession s = new CheckoutSession(id, orderId, customerId, currency, groups, now, 0);
        s.status = SessionStatus.OPEN;
        s.items = mergeItems(items);
        s.totals = Totals.zero(currency);
        s.updatedAt = now;
        s.expiresAt = now.plus(ttl);
        return s;
    }

    /** Phiên mở sẵn cho một suất flash sale: id = order id = claim id, sống tới hạn của claim. */
    public static CheckoutSession openForFlashSale(FlashSale claim, String customerId, String currency, int quantity,
                                                   Instant now) {
        CheckoutSession s = open(claim.claimId(), claim.claimId(), customerId, List.of(), currency,
                List.of(new Item(claim.productId(), quantity)), now, Duration.between(now, claim.expiresAt()));
        s.flashSale = claim;
        return s;
    }

    static List<Item> mergeItems(List<Item> items) {
        if (items == null || items.isEmpty()) {
            throw CheckoutException.invalid("items must not be empty");
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Item i : items) {
            if (i.productId() == null || i.productId().isBlank() || i.quantity() <= 0) {
                throw CheckoutException.invalid("every item needs a productId and a positive quantity");
            }
            merged.merge(i.productId().trim(), i.quantity(), Integer::sum);
        }
        if (merged.size() > MAX_LINES) {
            throw CheckoutException.invalid("at most " + MAX_LINES + " different products per checkout");
        }
        List<Item> out = new ArrayList<>();
        merged.forEach((p, q) -> {
            if (q > MAX_QUANTITY) {
                throw CheckoutException.invalid("quantity of " + p + " must be at most " + MAX_QUANTITY);
            }
            out.add(new Item(p, q));
        });
        return List.copyOf(out);
    }

    /** Hết hạn thì chuyển EXPIRED (gọi trước mọi thao tác); trả về true nếu vừa hết hạn. */
    public boolean expireIfDue(Instant now) {
        if (status == SessionStatus.OPEN && !now.isBefore(expiresAt)) {
            status = SessionStatus.EXPIRED;
            updatedAt = now;
            return true;
        }
        return false;
    }

    public void requireOpen(Instant now) {
        expireIfDue(now);
        if (status != SessionStatus.OPEN) {
            throw CheckoutException.conflict("SESSION_" + status.name(), "checkout session is " + status.name().toLowerCase());
        }
    }

    public void changeItems(List<Item> items) {
        if (flashSale != null) {
            throw CheckoutException.conflict("FLASH_SALE_ITEMS_LOCKED",
                    "a flash sale checkout holds exactly the claimed product and quantity");
        }
        this.items = mergeItems(items);
    }

    public void changePromotion(String code) {
        this.promotionCode = code == null || code.isBlank() ? null : code.trim();
    }

    public void changeAddresses(Address shipping, Address billing) {
        this.shippingAddress = shipping;
        this.billingAddress = billing;
    }

    public void changeNote(String note) {
        this.note = note;
    }

    public void selectShipping(String carrier, String serviceCode) {
        this.selectedShipping = carrier == null ? null : carrier + ":" + serviceCode;
    }

    /** Áp báo giá: giữ lựa chọn vận chuyển nếu vẫn còn, không thì chọn rẻ nhất; gia hạn phiên. */
    public void applyQuote(Quote q, Instant now, Duration ttl) {
        this.lines = List.copyOf(q.lines());
        this.unpriced = List.copyOf(q.unpricedProductIds());
        this.promotionError = q.promotionError();
        this.shippingOptions = List.copyOf(q.shippingOptions());
        ShippingOption chosen = selected();
        if (chosen == null) {
            chosen = shippingOptions.stream().min(Comparator.comparing(o -> o.fee().amount())).orElse(null);
        }
        this.selectedShipping = chosen == null ? null : chosen.key();

        Money subtotal = Money.zero(currency);
        for (PricedLine l : lines) {
            subtotal = subtotal.plus(l.lineTotal());
        }
        Money discount = q.discount() == null ? Money.zero(currency) : q.discount().min(subtotal);
        Money fee = chosen == null ? Money.zero(currency) : chosen.fee();
        Money tax = Money.zero(currency);
        for (Money t : q.taxByLine().values()) {
            tax = tax.plus(t);
        }
        this.totals = new Totals(subtotal, discount, fee, tax, subtotal.minus(discount).plus(fee).plus(tax));
        this.updatedAt = now;
        // Phiên flash sale không gia hạn: quá hạn claim thì suất đã được trả cho người khác.
        this.expiresAt = flashSale != null ? flashSale.expiresAt() : now.plus(ttl);
    }

    public ShippingOption selected() {
        if (selectedShipping == null) {
            return null;
        }
        return shippingOptions.stream().filter(o -> o.key().equals(selectedShipping)).findFirst().orElse(null);
    }

    /** Điều kiện để đặt đơn: đủ giá, có địa chỉ và phương thức giao, voucher hợp lệ. */
    public void requireReadyToPlace() {
        if (!unpriced.isEmpty()) {
            throw CheckoutException.rejected("PRODUCT_UNAVAILABLE", "products without a price: " + String.join(", ", unpriced));
        }
        if (lines.isEmpty()) {
            throw CheckoutException.rejected("EMPTY_CHECKOUT", "nothing to order");
        }
        if (shippingAddress == null) {
            throw CheckoutException.rejected("ADDRESS_REQUIRED", "shippingAddress is required");
        }
        if (selected() == null) {
            throw CheckoutException.rejected("SHIPPING_UNAVAILABLE", "no shipping option for this address");
        }
        if (promotionError != null) {
            throw CheckoutException.rejected("PROMOTION_" + promotionError, "promotion code cannot be applied: " + promotionError);
        }
    }

    public void markPendingReview(Payment payment, FraudVerdict verdict, Instant now) {
        this.payment = payment;
        this.fraud = verdict;
        this.status = SessionStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    public void recordPayment(Payment payment, FraudVerdict verdict) {
        this.payment = payment;
        this.fraud = verdict;
    }

    public void markPlaced(String orderCode, Instant now) {
        if (status == SessionStatus.PLACED) {
            return;
        }
        if (status != SessionStatus.OPEN && status != SessionStatus.PENDING_REVIEW) {
            throw CheckoutException.conflict("SESSION_" + status.name(), "cannot place a " + status.name().toLowerCase() + " session");
        }
        this.orderCode = orderCode;
        this.status = SessionStatus.PLACED;
        this.updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        if (status.terminal()) {
            return;
        }
        this.status = SessionStatus.REJECTED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        if (status == SessionStatus.CANCELLED) {
            return;
        }
        if (status.terminal()) {
            throw CheckoutException.conflict("SESSION_" + status.name(), "checkout session is " + status.name().toLowerCase());
        }
        this.status = SessionStatus.CANCELLED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(String customerId) {
        return this.customerId.equals(customerId);
    }

    public String id() { return id; }
    public String orderId() { return orderId; }
    public String customerId() { return customerId; }
    public String currency() { return currency; }
    public List<String> customerGroups() { return customerGroups; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }
    public SessionStatus status() { return status; }
    public List<Item> items() { return items; }
    public List<PricedLine> lines() { return lines; }
    public List<String> unpriced() { return unpriced; }
    public String promotionCode() { return promotionCode; }
    public String promotionError() { return promotionError; }
    public Address shippingAddress() { return shippingAddress; }
    public Address billingAddress() { return billingAddress; }
    public String note() { return note; }
    public List<ShippingOption> shippingOptions() { return shippingOptions; }
    public String selectedShipping() { return selectedShipping; }
    public Totals totals() { return totals; }
    public Payment payment() { return payment; }
    public FraudVerdict fraud() { return fraud; }
    public String orderCode() { return orderCode; }
    public String failureReason() { return failureReason; }
    public Instant updatedAt() { return updatedAt; }
    public Instant expiresAt() { return expiresAt; }
    public FlashSale flashSale() { return flashSale; }

    /** Toàn bộ trạng thái để adapter persistence lưu (JSON) và dựng lại. */
    public record Snapshot(String id, String orderId, String customerId, String currency, List<String> customerGroups,
                           SessionStatus status, List<Item> items, List<PricedLine> lines, List<String> unpriced,
                           String promotionCode, String promotionError, Address shippingAddress, Address billingAddress,
                           String note, List<ShippingOption> shippingOptions, String selectedShipping, Totals totals,
                           Payment payment, FraudVerdict fraud, String orderCode, String failureReason,
                           Instant createdAt, Instant updatedAt, Instant expiresAt, long version, FlashSale flashSale) {

        public Snapshot withVersion(long v) {
            return new Snapshot(id, orderId, customerId, currency, customerGroups, status, items, lines, unpriced,
                    promotionCode, promotionError, shippingAddress, billingAddress, note, shippingOptions,
                    selectedShipping, totals, payment, fraud, orderCode, failureReason, createdAt, updatedAt, expiresAt,
                    v, flashSale);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(id, orderId, customerId, currency, customerGroups, status, items, lines, unpriced,
                promotionCode, promotionError, shippingAddress, billingAddress, note, shippingOptions, selectedShipping,
                totals, payment, fraud, orderCode, failureReason, createdAt, updatedAt, expiresAt, version, flashSale);
    }

    public static CheckoutSession restore(Snapshot s) {
        CheckoutSession x = new CheckoutSession(s.id(), s.orderId(), s.customerId(), s.currency(),
                s.customerGroups() == null ? List.of() : s.customerGroups(), s.createdAt(), s.version());
        x.status = s.status();
        x.items = s.items();
        x.lines = s.lines() == null ? List.of() : s.lines();
        x.unpriced = s.unpriced() == null ? List.of() : s.unpriced();
        x.promotionCode = s.promotionCode();
        x.promotionError = s.promotionError();
        x.shippingAddress = s.shippingAddress();
        x.billingAddress = s.billingAddress();
        x.note = s.note();
        x.shippingOptions = s.shippingOptions() == null ? List.of() : s.shippingOptions();
        x.selectedShipping = s.selectedShipping();
        x.totals = s.totals();
        x.payment = s.payment();
        x.fraud = s.fraud();
        x.orderCode = s.orderCode();
        x.failureReason = s.failureReason();
        x.updatedAt = s.updatedAt();
        x.expiresAt = s.expiresAt();
        x.flashSale = s.flashSale();
        return x;
    }
}
