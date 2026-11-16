package com.congthanh.checkoutservice.application.service;

import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase;
import com.congthanh.checkoutservice.application.port.out.Catalog;
import com.congthanh.checkoutservice.application.port.out.CheckoutWorkflows;
import com.congthanh.checkoutservice.application.port.out.FraudCheck;
import com.congthanh.checkoutservice.application.port.out.Ids;
import com.congthanh.checkoutservice.application.port.out.Orders;
import com.congthanh.checkoutservice.application.port.out.Pricing;
import com.congthanh.checkoutservice.application.port.out.Promotions;
import com.congthanh.checkoutservice.application.port.out.SessionRepository;
import com.congthanh.checkoutservice.application.port.out.Shipping;
import com.congthanh.checkoutservice.application.port.out.Tax;
import com.congthanh.checkoutservice.domain.exception.CheckoutException;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.FraudVerdict;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.Payment;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.PricedLine;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.ShippingOption;
import com.congthanh.checkoutservice.domain.model.Money;
import com.congthanh.checkoutservice.domain.model.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Điều phối phiên checkout: báo giá từ pricing/promotion/shipping/tax, kiểm tra gian lận rồi tạo đơn ở
 * order-service. Saga sau khi có đơn (giữ hàng, thanh toán, xác nhận) là workflow Temporal của service này.
 */
public class CheckoutService implements CheckoutUseCase, com.congthanh.checkoutservice.application.port.in.SubscriptionOrders {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final SessionRepository sessions;
    private final Catalog catalog;
    private final Pricing pricing;
    private final Promotions promotions;
    private final Shipping shipping;
    private final Tax tax;
    private final FraudCheck fraud;
    private final Orders orders;
    private final CheckoutWorkflows workflows;
    private final Ids ids;
    private final Clock clock;
    private final Settings settings;
    private com.congthanh.checkoutservice.application.port.out.Customers customers;

    public record Settings(Duration sessionTtl) {
    }

    /** Đặt sau khi khởi tạo để không đổi chữ ký constructor (test hiện có dựng service không có port này). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCustomers(com.congthanh.checkoutservice.application.port.out.Customers customers) {
        this.customers = customers;
    }

    @Override
    public Result place(Command cmd) {
        if (customers == null) {
            throw CheckoutException.unavailable("customer address book is not configured");
        }
        if (cmd.items() == null || cmd.items().isEmpty()) {
            throw CheckoutException.invalid("a subscription order needs items");
        }
        String sessionId = java.util.UUID.nameUUIDFromBytes(("subscription-session:" + cmd.idempotencyKey())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        Optional<CheckoutSession> existing = sessions.findById(sessionId);
        if (existing.isPresent() && existing.get().status() == SessionStatus.PLACED) {
            CheckoutSession s = existing.get();
            return new Result(s.orderId(), s.orderCode(), s.totals().total().amount(), s.currency());
        }
        String orderId = java.util.UUID.nameUUIDFromBytes(("subscription-order:" + cmd.idempotencyKey())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        String currency = requireCurrency(cmd.currency());
        CheckoutSession s = existing.orElseGet(() -> CheckoutSession.open(sessionId, orderId, cmd.customerId(), List.of(),
                currency, cmd.items(), clock.instant(), settings.sessionTtl()));
        s.changeAddresses(customers.shippingAddress(cmd.customerId(), cmd.addressId()), null);
        s.changeNote("subscription " + cmd.subscriptionId());
        s.changePromotion(cmd.promotionCode() == null || cmd.promotionCode().isBlank() ? null : cmd.promotionCode());
        requote(s);
        // Không có ai chọn: lấy cách giao rẻ nhất (cùng giá thì nhanh hơn).
        s.shippingOptions().stream()
                .min(java.util.Comparator.comparing((CheckoutSession.ShippingOption o) -> o.fee().amount())
                        .thenComparingInt(CheckoutSession.ShippingOption::daysMax))
                .ifPresent(o -> s.selectShipping(o.carrier(), o.serviceCode()));
        requote(s);
        s.requireReadyToPlace();
        if (s.promotionError() != null) {
            // Mã subscriber hết hạn/hết lượt: vẫn giao, không giảm (khách đã đồng ý giá hiện hành khi đăng ký).
            log.warn("subscription {} promotion {} not applied: {}", cmd.subscriptionId(), cmd.promotionCode(), s.promotionError());
            s.changePromotion(null);
            requote(s);
        }
        // Đơn đầu tiên đã qua fraud-service; đơn gia hạn dùng cùng phương thức đã lưu nên không chấm lại.
        s.recordPayment(new Payment("CARD", cmd.paymentMethodRef(), null, null, null, null, null), null);
        CheckoutSession placed = placeOrder(s);
        return new Result(placed.orderId(), placed.orderCode(), placed.totals().total().amount(), placed.currency());
    }

    public CheckoutService(SessionRepository sessions, Catalog catalog, Pricing pricing, Promotions promotions,
                           Shipping shipping, Tax tax, FraudCheck fraud, Orders orders, CheckoutWorkflows workflows,
                           Ids ids, Clock clock, Settings settings) {
        this.sessions = sessions;
        this.catalog = catalog;
        this.pricing = pricing;
        this.promotions = promotions;
        this.shipping = shipping;
        this.tax = tax;
        this.fraud = fraud;
        this.orders = orders;
        this.workflows = workflows;
        this.ids = ids;
        this.clock = clock;
        this.settings = settings;
    }

    /** Mọi phiên checkout phải nêu rõ đơn vị tiền; không có giá trị mặc định. */
    private static String requireCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw CheckoutException.invalid("currency is required");
        }
        String c = currency.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z]{3}")) {
            throw CheckoutException.invalid("currency must be an ISO 4217 code");
        }
        return c;
    }

    @Override
    public CheckoutSession open(Customer customer, OpenCommand cmd) {
        String currency = requireCurrency(cmd.currency());
        CheckoutSession s = CheckoutSession.open(ids.newId(), ids.newId(), customer.id(), customer.groups(), currency,
                cmd.items(), clock.instant(), settings.sessionTtl());
        s.changePromotion(cmd.promotionCode());
        s.changeAddresses(cmd.shippingAddress(), cmd.billingAddress());
        s.changeNote(cmd.note());
        requote(s);
        return sessions.save(s);
    }

    @Override
    public CheckoutSession update(Customer customer, String sessionId, UpdateCommand cmd) {
        CheckoutSession s = load(customer, sessionId);
        s.requireOpen(clock.instant());
        if (cmd.items() != null) {
            s.changeItems(cmd.items());
        }
        if (cmd.clearPromotion()) {
            s.changePromotion(null);
        } else if (cmd.promotionCode() != null) {
            s.changePromotion(cmd.promotionCode());
        }
        if (cmd.shippingAddress() != null) {
            s.changeAddresses(cmd.shippingAddress(), cmd.billingAddress() != null ? cmd.billingAddress() : s.billingAddress());
        } else if (cmd.billingAddress() != null) {
            s.changeAddresses(s.shippingAddress(), cmd.billingAddress());
        }
        if (cmd.shippingCarrier() != null) {
            s.selectShipping(cmd.shippingCarrier(), cmd.shippingServiceCode());
        }
        if (cmd.note() != null) {
            s.changeNote(cmd.note());
        }
        requote(s);
        if (cmd.shippingCarrier() != null && (s.selected() == null
                || !s.selected().key().equals(cmd.shippingCarrier() + ":" + cmd.shippingServiceCode()))) {
            throw CheckoutException.invalid("shipping option " + cmd.shippingCarrier() + "/" + cmd.shippingServiceCode()
                    + " is not available for this address");
        }
        return sessions.save(s);
    }

    @Override
    public View get(Customer customer, String sessionId) {
        CheckoutSession s = load(customer, sessionId);
        if (s.expireIfDue(clock.instant())) {
            s = sessions.save(s);
        }
        Optional<CheckoutWorkflows.Progress> progress = s.status() == SessionStatus.PLACED
                ? workflows.progress(s.orderId()) : Optional.empty();
        return new View(s, progress.orElse(null));
    }

    @Override
    public Placement place(Customer customer, String sessionId, PlaceCommand cmd) {
        CheckoutSession s = load(customer, sessionId);
        if (s.status() == SessionStatus.PLACED) {
            return new Placement(s, Outcome.PLACED); // gọi lại (retry) sau khi đã đặt: trả kết quả cũ
        }
        if (s.status() == SessionStatus.PENDING_REVIEW) {
            return new Placement(s, Outcome.PENDING_REVIEW);
        }
        s.requireOpen(clock.instant());
        if (cmd.paymentMethod() == null || cmd.paymentMethod().isBlank()) {
            throw CheckoutException.invalid("paymentMethod is required");
        }
        // Báo giá lại ngay trước khi đặt: giá, voucher, phí giao có thể đã đổi từ lúc khách xem.
        requote(s);
        s.requireReadyToPlace();
        if (cmd.expectedTotal() != null && !sameAmount(cmd.expectedTotal(), s.totals().total())) {
            sessions.save(s);
            throw CheckoutException.conflict("PRICE_CHANGED", "the total changed to " + s.totals().total().amount()
                    + " " + s.currency() + "; review the checkout and place again");
        }
        if (cmd.storeCredit() != null && (cmd.storeCredit().signum() < 0
                || cmd.storeCredit().compareTo(s.totals().total().amount()) > 0)) {
            throw CheckoutException.invalid("storeCredit must be between 0 and the order total");
        }
        if (cmd.loyaltyPoints() != null && cmd.loyaltyPoints() < 0) {
            throw CheckoutException.invalid("loyaltyPoints must not be negative");
        }
        Payment payment = new Payment(cmd.paymentMethod().trim().toUpperCase(Locale.ROOT), cmd.paymentToken(),
                cmd.returnUrl(), cmd.clientIp(), cmd.deviceId(), cmd.storeCredit(), cmd.loyaltyPoints());
        FraudVerdict verdict = fraud.assess(s, new FraudCheck.Context(customer.email(), s.shippingAddress().phone(),
                cmd.clientIp(), cmd.deviceId(), payment.method(), customer.accountCreatedAt()));
        switch (verdict.decision()) {
            case "REJECT" -> {
                s.recordPayment(null, verdict);
                s.reject("rejected by risk checks", clock.instant());
                sessions.save(s);
                throw CheckoutException.rejected("FRAUD_REJECTED", "this order cannot be placed; please contact support");
            }
            case "REVIEW" -> {
                s.markPendingReview(payment, verdict, clock.instant());
                return new Placement(sessions.save(s), Outcome.PENDING_REVIEW);
            }
            default -> {
                s.recordPayment(payment, verdict);
                return new Placement(placeOrder(s), Outcome.PLACED);
            }
        }
    }

    @Override
    public CheckoutSession cancel(Customer customer, String sessionId, String reason) {
        CheckoutSession s = load(customer, sessionId);
        String why = reason == null || reason.isBlank() ? "cancelled by customer" : reason;
        if (s.status() == SessionStatus.PLACED) {
            // Đơn đã tạo: nhờ saga huỷ (chỉ có tác dụng trước khi trừ kho).
            workflows.requestCancel(s.orderId(), why);
            return s;
        }
        s.cancel(why, clock.instant());
        return sessions.save(s);
    }

    @Override
    public void onFraudReviewDecided(String sessionId, boolean approved, String note) {
        Optional<CheckoutSession> found = sessions.findById(sessionId);
        if (found.isEmpty()) {
            log.warn("fraud review for unknown checkout session {}", sessionId);
            return;
        }
        CheckoutSession s = found.get();
        if (s.status() != SessionStatus.PENDING_REVIEW) {
            return; // đã xử lý (event giao lại) hoặc khách đã huỷ
        }
        if (!approved) {
            s.reject("rejected after manual review" + (note == null || note.isBlank() ? "" : ": " + note), clock.instant());
            sessions.save(s);
            return;
        }
        // Giữ nguyên báo giá lúc khách đặt: đã duyệt đúng số tiền đó.
        placeOrder(s);
    }

    @Override
    public void onFlashSaleClaimed(FlashSaleClaim claim) {
        if (!claim.expiresAt().isAfter(clock.instant())) {
            log.warn("flash sale claim {} already expired, no checkout session opened", claim.claimId());
            return;
        }
        if (sessions.findById(claim.claimId()).isPresent()) {
            return; // event giao lại
        }
        // Phiên flash sale tính theo đơn vị tiền của chính sản phẩm (product-service trả kèm giá), không có mặc định.
        Catalog.Product product = catalog.lookup(List.of(claim.productId())).get(claim.productId());
        if (product == null || product.currency() == null || product.currency().isBlank()) {
            log.warn("flash sale claim {}: product {} has no price currency, no checkout session opened",
                    claim.claimId(), claim.productId());
            return;
        }
        CheckoutSession s = CheckoutSession.openForFlashSale(
                new CheckoutSession.FlashSale(claim.saleId(), claim.productId(), claim.claimId(), claim.expiresAt()),
                claim.customerId(), product.currency(), claim.quantity(), clock.instant());
        try {
            requote(s);
        } catch (CheckoutException e) {
            if (e.kind() != CheckoutException.Kind.REJECTED && e.kind() != CheckoutException.Kind.INVALID) {
                throw e; // lỗi tạm thời: để Kafka giao lại
            }
            // Sản phẩm không bán được/không có giá: không mở phiên, claim tự hết hạn và suất quay về sale.
            log.warn("flash sale claim {} cannot be checked out: {}", claim.claimId(), e.getMessage());
            return;
        }
        sessions.save(s);
    }

    private CheckoutSession placeOrder(CheckoutSession s) {
        try {
            Orders.Placed placed = orders.place(s);
            s.markPlaced(placed.orderCode(), clock.instant());
            return sessions.save(s);
        } catch (CheckoutException e) {
            if (e.kind() == CheckoutException.Kind.REJECTED || e.kind() == CheckoutException.Kind.INVALID) {
                s.reject(e.getMessage(), clock.instant());
                sessions.save(s);
            } else {
                sessions.save(s); // UNAVAILABLE: giữ trạng thái để khách/consumer thử lại (order id cố định → idempotent)
            }
            throw e;
        }
    }

    /** Gọi pricing → promotion → shipping → tax và áp vào phiên. */
    void requote(CheckoutSession s) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        s.items().forEach(i -> quantities.put(i.productId(), i.quantity()));
        Map<String, Catalog.Product> products = catalog.lookup(quantities.keySet());
        for (var e : quantities.entrySet()) {
            Catalog.Product p = products.get(e.getKey());
            if (p == null || !p.purchasable()) {
                throw CheckoutException.rejected("PRODUCT_UNAVAILABLE", "product " + e.getKey() + " is not available");
            }
            if (p.maxQuantityPerOrder() > 0 && e.getValue() > p.maxQuantityPerOrder()) {
                throw CheckoutException.rejected("QUANTITY_LIMIT",
                        "at most " + p.maxQuantityPerOrder() + " of product " + e.getKey() + " per order");
            }
        }

        Pricing.Result priced = pricing.quote(quantities, s.currency(), s.customerGroups());
        List<PricedLine> lines = new ArrayList<>();
        Money subtotal = Money.zero(s.currency());
        int weight = 0;
        for (var e : quantities.entrySet()) {
            Pricing.Line l = priced.lines().get(e.getKey());
            if (l == null) {
                continue;
            }
            Catalog.Product p = products.get(e.getKey());
            lines.add(new PricedLine(p.productId(), p.sku(), p.name(), e.getValue(), l.unitPrice(), l.compareAt(),
                    l.lineTotal(), p.weightGrams(), p.taxClassId()));
            subtotal = subtotal.plus(l.lineTotal());
            weight += p.weightGrams() * e.getValue();
        }

        Money discount = Money.zero(s.currency());
        String promotionError = null;
        if (s.promotionCode() != null && !lines.isEmpty()) {
            Promotions.Quote q = promotions.quote(s.promotionCode(), s.customerId(), subtotal);
            if (q.valid()) {
                discount = q.discount().min(subtotal);
            } else {
                promotionError = q.reason() == null || q.reason().isBlank() ? "INVALID" : q.reason();
            }
        }

        List<ShippingOption> options = List.of();
        Map<String, Money> taxes = Map.of();
        if (s.shippingAddress() != null && !lines.isEmpty()) {
            options = shipping.quote(s.shippingAddress(), Math.max(weight, 1), subtotal.minus(discount));
            taxes = tax.calculate(s.shippingAddress(), taxable(lines, subtotal, discount));
        }
        s.applyQuote(new CheckoutSession.Quote(lines, priced.unpriced(), discount, promotionError, options, taxes),
                clock.instant(), settings.sessionTtl());
    }

    /** Giảm giá phân bổ theo tỷ lệ giá trị dòng; phần lẻ dồn vào dòng cuối để tổng khớp. */
    static List<Tax.TaxableLine> taxable(List<PricedLine> lines, Money subtotal, Money discount) {
        List<Tax.TaxableLine> out = new ArrayList<>();
        Money remaining = discount;
        for (int i = 0; i < lines.size(); i++) {
            PricedLine l = lines.get(i);
            Money share;
            if (i == lines.size() - 1 || subtotal.isZero()) {
                share = remaining;
            } else {
                BigDecimal ratio = l.lineTotal().amount().divide(subtotal.amount(), 12, RoundingMode.HALF_EVEN);
                share = new Money(discount.amount().multiply(ratio), discount.currency()).min(remaining);
            }
            remaining = remaining.minus(share);
            out.add(new Tax.TaxableLine(l.productId(), l.taxClassId(), l.lineTotal().minus(share)));
        }
        return out;
    }

    private static boolean sameAmount(Money a, Money b) {
        return a.currency().equals(b.currency()) && a.amount().compareTo(b.amount()) == 0;
    }

    private CheckoutSession load(Customer customer, String sessionId) {
        CheckoutSession s = sessions.findById(sessionId)
                .orElseThrow(() -> CheckoutException.notFound("checkout session " + sessionId + " not found"));
        if (!customer.canAccess(s)) {
            throw CheckoutException.notFound("checkout session " + sessionId + " not found");
        }
        return s;
    }
}
