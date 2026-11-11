package com.congthanh.checkoutservice.application.service;

import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.Customer;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.FlashSaleClaim;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.OpenCommand;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.Outcome;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.PlaceCommand;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.UpdateCommand;
import com.congthanh.checkoutservice.application.port.out.Catalog;
import com.congthanh.checkoutservice.application.port.out.CheckoutWorkflows;
import com.congthanh.checkoutservice.application.port.out.FraudCheck;
import com.congthanh.checkoutservice.application.port.out.Orders;
import com.congthanh.checkoutservice.application.port.out.Pricing;
import com.congthanh.checkoutservice.application.port.out.Promotions;
import com.congthanh.checkoutservice.application.port.out.SessionRepository;
import com.congthanh.checkoutservice.application.port.out.Shipping;
import com.congthanh.checkoutservice.application.port.out.Tax;
import com.congthanh.checkoutservice.domain.exception.CheckoutException;
import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.FraudVerdict;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.Item;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.ShippingOption;
import com.congthanh.checkoutservice.domain.model.Money;
import com.congthanh.checkoutservice.domain.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutServiceTest {

    private static final Address NYC = new Address("Alex Johnson", "+12125550100", "350 5th Ave", null, null, null, "NY", "us", "10118");
    private static final Customer ME = new Customer("c-1", List.of("CUSTOMER"), "me@example.com", null, false);

    private final Map<String, CheckoutSession> store = new HashMap<>();
    private final Map<String, Money> prices = new HashMap<>(Map.of("p-1", Money.of("100", "USD"), "p-2", Money.of("50", "USD")));
    private final List<String> placed = new ArrayList<>();
    private final List<String> cancelled = new ArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();
    private String fraudDecision = "APPROVE";
    private MutableClock clock;
    private CheckoutService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-10T10:00:00Z"));
        SessionRepository repo = new SessionRepository() {
            @Override
            public Optional<CheckoutSession> findById(String id) {
                return Optional.ofNullable(store.get(id)).map(s -> CheckoutSession.restore(s.snapshot()));
            }

            @Override
            public CheckoutSession save(CheckoutSession s) {
                var snap = s.snapshot();
                var stored = store.get(s.id());
                if (stored != null && stored.version() != snap.version()) {
                    throw CheckoutException.conflict("CONCURRENT_MODIFICATION", "conflict");
                }
                var next = CheckoutSession.restore(snap.withVersion(snap.version() + 1));
                store.put(s.id(), next);
                return next;
            }
        };
        Catalog catalog = idsIn -> {
            Map<String, Catalog.Product> out = new HashMap<>();
            idsIn.forEach(id -> out.put(id, new Catalog.Product(id, "SKU-" + id, "Product " + id, !id.equals("gone"), 5, 1000, 1)));
            return out;
        };
        Pricing pricing = (q, currency, groups) -> {
            Map<String, Pricing.Line> lines = new HashMap<>();
            List<String> unpriced = new ArrayList<>();
            q.forEach((p, n) -> {
                Money unit = prices.get(p);
                if (unit == null) {
                    unpriced.add(p);
                } else {
                    lines.put(p, new Pricing.Line(p, n, unit, unit, unit.times(n)));
                }
            });
            return new Pricing.Result(lines, unpriced);
        };
        Promotions promotions = (code, customer, subtotal) -> code.equals("SALE10")
                ? new Promotions.Quote(true, Money.of("25", "USD"), null)
                : new Promotions.Quote(false, Money.zero("USD"), "EXPIRED");
        Shipping shipping = (dest, grams, value) -> List.of(
                new ShippingOption("internal", "express", "Express", Money.of("50", "USD"), 1, 1),
                new ShippingOption("internal", "standard", "Standard", Money.of("30", "USD"), 2, 4));
        Tax tax = (addr, lines) -> {
            Map<String, Money> out = new HashMap<>();
            lines.forEach(l -> out.put(l.lineId(), new Money(l.taxable().amount().multiply(new java.math.BigDecimal("0.1")), "USD")));
            return out;
        };
        FraudCheck fraud = (s, ctx) -> new FraudVerdict("a-1", fraudDecision, 60, List.of("TEST"));
        Orders orders = s -> {
            placed.add(s.orderId());
            return new Orders.Placed(s.orderId(), "CT-" + s.orderId(), "PENDING");
        };
        CheckoutWorkflows workflows = new CheckoutWorkflows() {
            @Override
            public Optional<Progress> progress(String orderId) {
                return Optional.of(new Progress("AWAITING_PAYMENT", "https://pay/redirect", null));
            }

            @Override
            public void requestCancel(String orderId, String reason) {
                cancelled.add(orderId);
            }
        };
        service = new CheckoutService(repo, catalog, pricing, promotions, shipping, tax, fraud, orders, workflows,
                () -> "id-" + ids.incrementAndGet(), clock, new CheckoutService.Settings("USD", Duration.ofMinutes(30)));
    }

    private CheckoutSession open() {
        return service.open(ME, new OpenCommand(List.of(new Item("p-1", 2), new Item("p-2", 1), new Item("p-1", 0 + 1)),
                null, "SALE10", NYC, null, null));
    }

    @Test
    void openQuotesEverything() {
        CheckoutSession s = open();

        assertThat(s.items()).containsExactly(new Item("p-1", 3), new Item("p-2", 1));
        var t = s.totals();
        assertThat(t.subtotal()).isEqualTo(Money.of("350", "USD"));
        assertThat(t.discount()).isEqualTo(Money.of("25", "USD"));
        assertThat(t.shippingFee()).isEqualTo(Money.of("30", "USD")); 
        assertThat(t.tax()).isEqualTo(Money.of("32.5", "USD"));      
        assertThat(t.total()).isEqualTo(Money.of("387.5", "USD"));
        assertThat(s.shippingAddress().country()).isEqualTo("US");
    }

    @Test
    void placeRequotesAndRejectsChangedTotal() {
        CheckoutSession s = open();
        prices.put("p-2", Money.of("60", "USD"));

        assertThatThrownBy(() -> service.place(ME, s.id(), new PlaceCommand("CARD", "ctoken_test", null, "1.2.3.4", null,
                Money.of("387.5", "USD"))))
                .isInstanceOf(CheckoutException.class).extracting("code").isEqualTo("PRICE_CHANGED");
        assertThat(store.get(s.id()).totals().total()).isEqualTo(Money.of("398.5", "USD"));
        assertThat(placed).isEmpty();
    }

    @Test
    void approvedPlacementCreatesOrderOnceAndShowsProgress() {
        CheckoutSession s = open();
        var cmd = new PlaceCommand("CARD", "ctoken_test", null, "1.2.3.4", "dev-1", Money.of("387.5", "USD"));

        var p = service.place(ME, s.id(), cmd);
        var again = service.place(ME, s.id(), cmd);

        assertThat(p.outcome()).isEqualTo(Outcome.PLACED);
        assertThat(again.session().orderCode()).isEqualTo(p.session().orderCode());
        assertThat(placed).containsExactly(s.orderId());
        var view = service.get(ME, s.id());
        assertThat(view.progress().paymentRedirectUrl()).isEqualTo("https://pay/redirect");

        service.cancel(ME, s.id(), null);
        assertThat(cancelled).containsExactly(s.orderId());
    }

    @Test
    void reviewHoldsUntilAnalystDecides() {
        fraudDecision = "REVIEW";
        CheckoutSession s = open();

        var p = service.place(ME, s.id(), new PlaceCommand("PAYPAL", null, null, null, null, null));
        assertThat(p.outcome()).isEqualTo(Outcome.PENDING_REVIEW);
        assertThat(placed).isEmpty();

        service.onFraudReviewDecided(s.id(), true, "verified by phone");
        service.onFraudReviewDecided(s.id(), true, "redelivered event");
        assertThat(placed).containsExactly(s.orderId());
        assertThat(store.get(s.id()).status()).isEqualTo(SessionStatus.PLACED);
    }

    @Test
    void rejectedByFraudOrReview() {
        fraudDecision = "REJECT";
        CheckoutSession s = open();
        assertThatThrownBy(() -> service.place(ME, s.id(), new PlaceCommand("STRIPE", "t", null, null, null, null)))
                .extracting("code").isEqualTo("FRAUD_REJECTED");
        assertThat(store.get(s.id()).status()).isEqualTo(SessionStatus.REJECTED);

        fraudDecision = "REVIEW";
        CheckoutSession s2 = open();
        service.place(ME, s2.id(), new PlaceCommand("STRIPE", "t", null, null, null, null));
        service.onFraudReviewDecided(s2.id(), false, "stolen card");
        assertThat(store.get(s2.id()).status()).isEqualTo(SessionStatus.REJECTED);
        assertThat(store.get(s2.id()).failureReason()).contains("stolen card");
        assertThat(placed).isEmpty();
    }

    @Test
    void invalidVoucherBlocksPlacementButNotQuoting() {
        CheckoutSession s = open();
        var updated = service.update(ME, s.id(), new UpdateCommand(null, "OLD", false, null, null, "internal", "express", null));

        assertThat(updated.promotionError()).isEqualTo("EXPIRED");
        assertThat(updated.totals().shippingFee()).isEqualTo(Money.of("50", "USD"));
        assertThatThrownBy(() -> service.place(ME, s.id(), new PlaceCommand("STRIPE", "t", null, null, null, null)))
                .extracting("code").isEqualTo("PROMOTION_EXPIRED");
        assertThatThrownBy(() -> service.update(ME, s.id(), new UpdateCommand(null, null, true, null, null, "ghn", "x", null)))
                .isInstanceOf(CheckoutException.class).extracting("kind").isEqualTo(CheckoutException.Kind.INVALID);
    }

    @Test
    void flashSaleClaimOpensLockedSessionThatExpiresWithTheClaim() {
        var claim = new FlashSaleClaim("0198c5a0-0000-7000-8000-000000000001", "sale-1", "p-1", ME.id(),
                1, clock.instant().plus(Duration.ofMinutes(10)));
        service.onFlashSaleClaimed(claim);
        service.onFlashSaleClaimed(claim); // event giao lại

        CheckoutSession s = service.get(ME, claim.claimId()).session();
        assertThat(s.orderId()).isEqualTo(claim.claimId());
        assertThat(s.items()).containsExactly(new Item("p-1", 1));
        assertThat(s.flashSale().saleId()).isEqualTo("sale-1");
        assertThat(s.totals().subtotal()).isEqualTo(Money.of("100", "USD"));

        assertThatThrownBy(() -> service.update(ME, s.id(), new UpdateCommand(List.of(new Item("p-1", 5)),
                null, false, null, null, null, null, null))).extracting("code").isEqualTo("FLASH_SALE_ITEMS_LOCKED");
        // Thêm địa chỉ báo giá lại nhưng không gia hạn quá hạn của claim.
        CheckoutSession updated = service.update(ME, s.id(), new UpdateCommand(null, null, false, NYC,
                null, null, null, null));
        assertThat(updated.expiresAt()).isEqualTo(claim.expiresAt());

        clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> service.place(ME, s.id(), new PlaceCommand("CARD", "t", null, null, null, null)))
                .extracting("code").isEqualTo("SESSION_EXPIRED");
    }

    @Test
    void sessionsExpireAndArePrivate() {
        CheckoutSession s = open();
        var other = new Customer("c-2", List.of("CUSTOMER"), null, null, false);
        assertThatThrownBy(() -> service.get(other, s.id())).extracting("kind").isEqualTo(CheckoutException.Kind.NOT_FOUND);

        clock.advance(Duration.ofMinutes(31));
        assertThat(service.get(ME, s.id()).session().status()).isEqualTo(SessionStatus.EXPIRED);
        assertThatThrownBy(() -> service.place(ME, s.id(), new PlaceCommand("STRIPE", "t", null, null, null, null)))
                .extracting("code").isEqualTo("SESSION_EXPIRED");
    }

    @Test
    void unavailableAndUnpricedProducts() {
        assertThatThrownBy(() -> service.open(ME, new OpenCommand(List.of(new Item("gone", 1)), null, null, null, null, null)))
                .extracting("code").isEqualTo("PRODUCT_UNAVAILABLE");
        assertThatThrownBy(() -> service.open(ME, new OpenCommand(List.of(new Item("p-1", 6)), null, null, null, null, null)))
                .extracting("code").isEqualTo("QUANTITY_LIMIT");
        CheckoutSession s = service.open(ME, new OpenCommand(List.of(new Item("p-1", 1), new Item("new", 1)), null, null, NYC, null, null));
        assertThat(s.unpriced()).containsExactly("new");
        assertThatThrownBy(() -> service.place(ME, s.id(), new PlaceCommand("STRIPE", "t", null, null, null, null)))
                .extracting("code").isEqualTo("PRODUCT_UNAVAILABLE");
    }

    @Test
    void discountIsSpreadOverLinesForTax() {
        var lines = List.of(
                new CheckoutSession.PricedLine("a", null, "A", 1, Money.of("1.00", "USD"), null, Money.of("1.00", "USD"), 0, 1),
                new CheckoutSession.PricedLine("b", null, "B", 1, Money.of("2.00", "USD"), null, Money.of("2.00", "USD"), 0, 1));
        var taxable = CheckoutService.taxable(lines, Money.of("3.00", "USD"), Money.of("1.00", "USD"));
        assertThat(taxable).extracting(Tax.TaxableLine::taxable)
                .containsExactly(Money.of("0.67", "USD"), Money.of("1.33", "USD"));
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
