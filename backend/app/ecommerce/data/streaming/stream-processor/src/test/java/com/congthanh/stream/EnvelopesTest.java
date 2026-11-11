package com.congthanh.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.congthanh.cartservice.grpc.CartItemAdded;
import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.order.v1.OrderLine;
import com.congthanh.ecommerce.order.v1.OrderPlaced;
import com.congthanh.ecommerce.order.v1.ShippingAddress;
import com.congthanh.ecommerce.payment.v1.PaymentFailed;
import com.congthanh.ecommerce.stream.v1.StockCover;
import com.congthanh.inventoryservice.grpc.InventoryCommitted;
import com.congthanh.inventoryservice.grpc.ReservedLine;
import com.congthanh.inventoryservice.grpc.StockLevelChanged;
import com.congthanh.stream.io.Envelopes;
import com.congthanh.stream.model.InteractionSignal;
import com.congthanh.stream.model.InventorySignal;
import com.congthanh.stream.model.OrderSignal;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnvelopesTest {
    static byte[] env(Message m, long millis) {
        return EventEnvelope.newBuilder()
                .setId("e1")
                .setType(m.getDescriptorForType().getFullName())
                .setOccurredAt(Envelopes.ts(millis))
                .setPayload(Any.pack(m))
                .build()
                .toByteArray();
    }

    static final OrderPlaced PLACED = OrderPlaced.newBuilder()
            .setOrderId("o1").setCustomerId("c1")
            .setTotal(Money.newBuilder().setCurrencyCode("USD").setUnits(12).setNanos(340_000_000))
            .setShippingAddress(ShippingAddress.newBuilder().setCountryCode("US").setLine1("350 5th Ave").setPhone("+12125550100"))
            .addLines(OrderLine.newBuilder().setProductId("p1").setQuantity(2))
            .addLines(OrderLine.newBuilder().setProductId("p2").setQuantity(1))
            .build();

    @Test
    void decodesOrderPlacedWithMinorUnitsAndTimestamp() {
        List<OrderSignal> s = Envelopes.orderSignals(env(PLACED, 1_700_000_000_123L), 0);
        assertThat(s).hasSize(1);
        OrderSignal o = s.getFirst();
        assertThat(o.kind).isEqualTo(OrderSignal.PLACED);
        assertThat(o.amountMinor).isEqualTo(1234);
        assertThat(o.ts).isEqualTo(1_700_000_000_123L);
        assertThat(o.addressFp).hasSize(24);
    }

    @Test
    void paymentFailedHasNoCustomer() {
        List<OrderSignal> s = Envelopes.orderSignals(env(PaymentFailed.newBuilder().setOrderId("o1").build(), 5), 0);
        assertThat(s).singleElement().satisfies(o -> {
            assertThat(o.kind).isEqualTo(OrderSignal.PAYMENT_FAILED);
            assertThat(o.customerId).isEmpty();
        });
    }

    @Test
    void garbageAndUnknownEventsAreSkipped() {
        assertThat(Envelopes.orderSignals(new byte[] {1, 2, 3, 4}, 0)).isEmpty();
        assertThat(Envelopes.orderSignals(null, 0)).isEmpty();
        assertThat(Envelopes.orderSignals(env(CartItemAdded.newBuilder().setProductId("p").build(), 0), 0)).isEmpty();
    }

    @Test
    void interactionsFromOrderLinesAndCart() {
        assertThat(Envelopes.interactionSignals(env(PLACED, 1), 0)).extracting(x -> x.productId + ":" + x.units)
                .containsExactly("p1:2", "p2:1");
        List<InteractionSignal> cart = Envelopes.interactionSignals(env(CartItemAdded.newBuilder().setProductId("p9").setQuantity(3).build(), 1), 0);
        assertThat(cart).singleElement().satisfies(x -> assertThat(x.kind).isEqualTo(InteractionSignal.CART));
    }

    @Test
    void inventoryLevelsAndCommittedLines() {
        List<InventorySignal> lvl = Envelopes.inventorySignals(env(StockLevelChanged.newBuilder()
                .setProductId("p1").setWarehouseId("w1").setQuantity(50).setAvailable(40).build(), 1), 0);
        assertThat(lvl).singleElement().satisfies(x -> assertThat(x.quantity).isEqualTo(40));
        List<InventorySignal> sold = Envelopes.inventorySignals(env(InventoryCommitted.newBuilder()
                .addLines(ReservedLine.newBuilder().setProductId("p1").setWarehouseId("w1").setQuantity(3))
                .build(), 1), 0);
        assertThat(sold).singleElement().satisfies(x -> assertThat(x.kind).isEqualTo(InventorySignal.SOLD));
    }

    @Test
    void wrapIsDeterministicAndUnpackable() throws Exception {
        StockCover m = StockCover.newBuilder().setProductId("p1").setAvailable(3).build();
        byte[] a = Envelopes.wrap(m, "product", "p1", 42);
        byte[] b = Envelopes.wrap(m, "product", "p1", 42);
        EventEnvelope e = EventEnvelope.parseFrom(a);
        assertThat(e.getId()).isEqualTo(EventEnvelope.parseFrom(b).getId());
        assertThat(e.getType()).isEqualTo("ecommerce.stream.v1.StockCover");
        assertThat(e.getSource()).isEqualTo("stream-processor");
        assertThat(e.getPayload().unpack(StockCover.class).getAvailable()).isEqualTo(3);
    }
}
