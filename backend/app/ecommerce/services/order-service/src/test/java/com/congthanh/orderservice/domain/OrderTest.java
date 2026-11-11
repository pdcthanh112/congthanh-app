package com.congthanh.orderservice.domain;

import com.congthanh.orderservice.domain.event.OrderEvent;
import com.congthanh.orderservice.domain.exception.OrderException;
import com.congthanh.orderservice.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private static final Address ADDRESS = new Address("Alex Johnson", "+12125550100", "350 5th Ave", null,
            null, null, "NY", "US", "10118");
    private static final CheckoutDetails CARD = new CheckoutDetails(PaymentMethod.CARD, null, null, null);

    private static Order place(Money discount) {
        return Order.place("o-1", "CT-261109-AAAA0001", "c-1",
                List.of(new OrderLine("p-1", "SKU1", "T-Shirt", 2, Money.of("100", "USD")),
                        new OrderLine("p-2", "SKU2", "Jeans", 1, Money.of("50", "USD"))),
                discount == null ? null : "SALE10", discount, CARD, ADDRESS, null, null, "checkout-o-1", NOW);
    }

    @Test
    void placeComputesTotalsAndEmitsOrderPlaced() {
        Order order = place(Money.of("25", "USD"));

        assertThat(order.subtotal()).isEqualTo(Money.of("250", "USD"));
        assertThat(order.total()).isEqualTo(Money.of("225", "USD"));
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.billingAddress()).isEqualTo(ADDRESS);
        assertThat(order.pullEvents()).singleElement().isInstanceOf(OrderEvent.OrderPlaced.class);
        assertThat(order.pullEvents()).isEmpty();
    }

    @Test
    void totalIncludesShippingFeeAndTaxAndEventCarriesAddress() {
        Order order = Order.place("o-2", "CT-261109-AAAA0002", "c-1",
                List.of(new OrderLine("p-1", "SKU1", "T-Shirt", 2, Money.of("100", "USD"))),
                "SALE10", Money.of("20", "USD"), Money.of("30", "USD"), Money.of("16", "USD"),
                CARD, ADDRESS, null, null, "checkout-o-2", NOW);

        assertThat(order.total()).isEqualTo(Money.of("226", "USD"));
        assertThat(order.shippingFee()).isEqualTo(Money.of("30", "USD"));
        assertThat(order.tax()).isEqualTo(Money.of("16", "USD"));
        var placed = (OrderEvent.OrderPlaced) order.pullEvents().getFirst();
        assertThat(placed.shippingAddress()).isEqualTo(ADDRESS);
        assertThat(placed.total()).isEqualTo(order.total());
    }

    @Test
    void discountCannotExceedSubtotal() {
        assertThatThrownBy(() -> place(Money.of("300", "USD")))
                .isInstanceOf(OrderException.class)
                .extracting("kind").isEqualTo(OrderException.Kind.INVALID);
    }

    @Test
    void mixedCurrenciesAreRejected() {
        assertThatThrownBy(() -> Order.place("o-1", "c", "c-1",
                List.of(new OrderLine("p-1", null, "A", 1, Money.of("1", "EUR")),
                        new OrderLine("p-2", null, "B", 1, Money.of("1", "USD"))),
                null, null, CARD, ADDRESS, null, null, "w", NOW))
                .isInstanceOf(OrderException.class);
    }

    @Test
    void happyLifecycleIsIdempotent() {
        Order order = place(null);
        order.pullEvents();

        order.markReserved("checkout-o-1", NOW);
        order.markReserved("checkout-o-1", NOW);
        order.confirm("pay-1", NOW);
        order.confirm("pay-1", NOW);

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.paymentId()).isEqualTo("pay-1");
        assertThat(order.pullEvents()).singleElement().isInstanceOf(OrderEvent.OrderConfirmed.class);
    }

    @Test
    void cannotConfirmBeforeReservation() {
        Order order = place(null);
        assertThatThrownBy(() -> order.confirm("pay-1", NOW))
                .extracting("kind").isEqualTo(OrderException.Kind.CONFLICT);
    }

    @Test
    void cancelReleasesReservationButNotAfterConfirm() {
        Order cancelled = place(null);
        cancelled.markReserved("r-1", NOW);
        cancelled.pullEvents();
        cancelled.cancel("payment declined", NOW);
        cancelled.cancel("again", NOW);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("payment declined");
        assertThat(cancelled.pullEvents()).singleElement()
                .isEqualTo(new OrderEvent.OrderCancelled("o-1", "payment declined", "r-1"));

        Order confirmed = place(null);
        confirmed.markReserved("r-1", NOW);
        confirmed.confirm("pay-1", NOW);
        assertThatThrownBy(() -> confirmed.cancel("late", NOW))
                .extracting("kind").isEqualTo(OrderException.Kind.CONFLICT);
    }
}
