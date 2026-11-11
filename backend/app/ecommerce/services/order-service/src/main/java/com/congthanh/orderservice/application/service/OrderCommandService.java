package com.congthanh.orderservice.application.service;

import com.congthanh.ecommerce.workflow.checkout.CheckoutContract;
import com.congthanh.orderservice.application.port.in.CancelOrderUseCase;
import com.congthanh.orderservice.application.port.in.CheckoutStepsUseCase;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase.Priced;
import com.congthanh.orderservice.application.port.in.Requester;
import com.congthanh.orderservice.application.port.out.CheckoutOrchestrator;
import com.congthanh.orderservice.application.port.out.Inventory;
import com.congthanh.orderservice.application.port.out.OrderIdentity;
import com.congthanh.orderservice.application.port.out.OrderRepository;
import com.congthanh.orderservice.application.port.out.ProductCatalog;
import com.congthanh.orderservice.application.port.out.ProductCatalog.ProductSnapshot;
import com.congthanh.orderservice.application.port.out.Promotions;
import com.congthanh.orderservice.domain.exception.OrderException;
import com.congthanh.orderservice.domain.model.CheckoutDetails;
import com.congthanh.orderservice.domain.model.Money;
import com.congthanh.orderservice.domain.model.Order;
import com.congthanh.orderservice.domain.model.OrderLine;
import com.congthanh.orderservice.domain.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderCommandService implements PlaceOrderUseCase, CheckoutStepsUseCase, CancelOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);
    static final int MAX_LINES = 100;

    private final OrderRepository orders;
    private final ProductCatalog catalog;
    private final Promotions promotions;
    private final Inventory inventory;
    private final CheckoutOrchestrator checkout;
    private final OrderIdentity identity;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OrderCommandService(OrderRepository orders, ProductCatalog catalog, Promotions promotions,
                               Inventory inventory, CheckoutOrchestrator checkout, OrderIdentity identity,
                               TransactionTemplate tx, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.promotions = promotions;
        this.inventory = inventory;
        this.checkout = checkout;
        this.identity = identity;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * 1) lấy giá từ product-service, báo giá voucher; 2) lưu đơn PENDING + OrderPlaced (outbox) trong một
     * transaction; 3) sau commit mới start workflow. Nếu bước 3 lỗi, {@link CheckoutRecovery} start lại
     * (workflow id = checkout-&lt;orderId&gt; nên không bao giờ có hai saga cho một đơn).
     */
    @Override
    public PlacedOrder place(PlaceOrderCommand cmd) {
        Priced priced = cmd.priced();
        if (priced != null && priced.orderId() != null) {
            var existing = orders.findById(priced.orderId());
            if (existing.isPresent()) {
                Order o = existing.get();
                if (!o.isOwnedBy(cmd.customerId())) {
                    throw OrderException.conflict("order id " + priced.orderId() + " is already used");
                }
                return new PlacedOrder(o.id(), o.orderCode(), o.status(), o.total(), o.discount());
            }
        }
        Map<String, Integer> quantities = mergeItems(cmd.items());
        Map<String, ProductSnapshot> products = catalog.lookup(quantities.keySet());

        List<OrderLine> lines = new ArrayList<>();
        for (var e : quantities.entrySet()) {
            ProductSnapshot p = products.get(e.getKey());
            if (p == null || !p.purchasable()) {
                throw OrderException.rejected("PRODUCT_UNAVAILABLE", "product " + e.getKey() + " is not available");
            }
            if (p.maxQuantityPerOrder() > 0 && e.getValue() > p.maxQuantityPerOrder()) {
                throw OrderException.rejected("QUANTITY_LIMIT",
                        "at most " + p.maxQuantityPerOrder() + " of product " + e.getKey() + " per order");
            }
            Money unitPrice = p.price();
            if (priced != null && priced.unitPrices() != null) {
                unitPrice = priced.unitPrices().get(p.productId());
                if (unitPrice == null) {
                    throw OrderException.invalid("missing price for product " + p.productId());
                }
            }
            lines.add(new OrderLine(p.productId(), p.sku(), p.name(), e.getValue(), unitPrice));
        }

        Money subtotal = lines.stream().map(OrderLine::total).reduce(Money::plus).orElseThrow();
        Money discount = Money.zero(subtotal.currency());
        String promotionCode = blankToNull(cmd.promotionCode());
        if (promotionCode != null) {
            Promotions.DiscountQuote quote = promotions.quote(promotionCode, cmd.customerId(), subtotal);
            if (!quote.valid()) {
                throw OrderException.rejected("PROMOTION_" + quote.reason(), "promotion code cannot be applied: " + quote.reason());
            }
            discount = quote.discount();
        }

        String orderId = priced != null && priced.orderId() != null ? priced.orderId() : identity.newOrderId();
        Order order = Order.place(orderId, identity.newOrderCode(), cmd.customerId(), lines, promotionCode, discount,
                priced == null ? null : priced.shippingFee(), priced == null ? null : priced.tax(),
                new CheckoutDetails(cmd.paymentMethod(), cmd.paymentToken(), cmd.returnUrl(), cmd.clientIp()),
                cmd.shippingAddress(), cmd.billingAddress(), cmd.note(),
                CheckoutContract.workflowId(orderId), clock.instant());
        tx.executeWithoutResult(s -> orders.save(order));

        try {
            checkout.start(order);
        } catch (RuntimeException e) {
            log.warn("checkout start failed for order {}, recovery will retry: {}", orderId, e.toString());
        }
        return new PlacedOrder(order.id(), order.orderCode(), order.status(), order.total(), order.discount());
    }

    @Override
    public void redeemPromotion(String orderId) {
        Order order = load(orderId);
        if (!order.hasPromotion()) {
            return;
        }
        Promotions.DiscountQuote result = promotions.redeem(order.promotionCode(), order.customerId(), orderId, order.subtotal());
        if (!result.valid()) {
            throw OrderException.rejected("PROMOTION_" + result.reason(), "promotion could not be redeemed: " + result.reason());
        }
        if (!result.discount().equals(order.discount())) {
            // Voucher đổi giá trị giữa lúc báo giá và lúc redeem: không lặng lẽ tính tiền khác.
            throw OrderException.rejected("PROMOTION_CHANGED", "promotion value changed since the order was placed");
        }
    }

    @Override
    public void cancelPromotionRedemption(String orderId, String reason) {
        Order order = load(orderId);
        if (order.hasPromotion()) {
            promotions.cancelRedemption(orderId, reason);
        }
    }

    @Override
    public String reserveInventory(String orderId) {
        Order order = load(orderId);
        String reservationId = CheckoutContract.reservationId(orderId);
        Inventory.Reservation r = inventory.reserve(reservationId, order.customerId(), order.lines());
        if (!r.success()) {
            throw OrderException.rejected("OUT_OF_STOCK", r.message() == null ? "insufficient stock" : r.message());
        }
        tx.executeWithoutResult(s -> {
            Order fresh = load(orderId);
            fresh.markReserved(reservationId, clock.instant());
            orders.save(fresh);
        });
        return reservationId;
    }

    @Override
    public void releaseInventory(String orderId, String reason) {
        inventory.release(CheckoutContract.reservationId(orderId), reason);
    }

    @Override
    public void commitInventory(String orderId) {
        Order order = load(orderId);
        inventory.commit(CheckoutContract.reservationId(orderId), orderId, order.lines());
    }

    @Override
    public void confirm(String orderId, String paymentId) {
        tx.executeWithoutResult(s -> {
            Order order = load(orderId);
            order.confirm(paymentId, clock.instant());
            orders.save(order);
        });
    }

    @Override
    public void cancel(String orderId, String reason) {
        tx.executeWithoutResult(s -> {
            Order order = load(orderId);
            order.cancel(reason, clock.instant());
            orders.save(order);
        });
    }

    @Override
    public void requestCancel(String orderCode, Requester requester, String reason) {
        Order order = orders.findByCode(orderCode).orElseThrow(() -> OrderException.notFound("order " + orderCode + " not found"));
        if (!requester.canAccess(order.customerId())) {
            throw OrderException.notFound("order " + orderCode + " not found");
        }
        if (order.status() == OrderStatus.CONFIRMED) {
            throw OrderException.conflict("order is already confirmed");
        }
        if (order.status() == OrderStatus.CANCELLED) {
            return;
        }
        checkout.requestCancel(order.id(), reason == null || reason.isBlank() ? "cancelled by customer" : reason);
    }

    private Order load(String orderId) {
        return orders.findById(orderId).orElseThrow(() -> OrderException.notFound("order " + orderId + " not found"));
    }

    static Map<String, Integer> mergeItems(List<Item> items) {
        if (items == null || items.isEmpty()) {
            throw OrderException.invalid("items must not be empty");
        }
        if (items.size() > MAX_LINES) {
            throw OrderException.invalid("at most " + MAX_LINES + " items per order");
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Item i : items) {
            if (i.productId() == null || i.productId().isBlank() || i.quantity() <= 0) {
                throw OrderException.invalid("each item needs a productId and a positive quantity");
            }
            merged.merge(i.productId(), i.quantity(), Integer::sum);
        }
        return merged;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
