package com.congthanh.productservice.adapter.in.messaging;

import com.congthanh.catalogservice.grpc.CategoryUpserted;
import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.pricing.v1.PriceChanged;
import com.congthanh.inventoryservice.grpc.FlashSaleClaimed;
import com.congthanh.inventoryservice.grpc.StockLevelChanged;
import com.congthanh.productservice.application.port.in.ProductReadModelUseCase;
import com.congthanh.productservice.grpc.ProductDeleted;
import com.congthanh.productservice.grpc.ProductUpserted;
import com.congthanh.reviewservice.grpc.ProductRatingChanged;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProductReadModelConsumerTest {

    static final Instant OCCURRED = Instant.parse("2026-11-11T10:00:00Z");

    ProductReadModelUseCase readModel = mock(ProductReadModelUseCase.class);
    ProductReadModelConsumer consumer = new ProductReadModelConsumer(readModel);

    @Test
    void productEventsTriggerARefreshFromPostgres() {
        consumer.on(record("ecommerce.product.events", ProductUpserted.newBuilder().setProductId("p1").build()));
        consumer.on(record("ecommerce.product.events", ProductDeleted.newBuilder().setProductId("p2").build()));

        verify(readModel).refresh("p1");
        verify(readModel).refresh("p2");
    }

    @Test
    void priceChangeUsesEffectiveTimeAsVersion() {
        Instant effective = Instant.parse("2026-11-11T09:00:00Z");
        consumer.on(record("ecommerce.pricing.events", PriceChanged.newBuilder().setProductId("p1").setPriceList("default")
                .setNewPrice(Money.newBuilder().setCurrencyCode("USD").setUnits(19).setNanos(990_000_000))
                .setEffectiveAt(ts(effective)).build()));

        verify(readModel).onPriceChanged("p1", "default", new BigDecimal("19.99"), "USD", effective);
    }

    @Test
    void ratingStockAndCategoryUseTheirOwnEvents() {
        consumer.on(record("ecommerce.review.events", ProductRatingChanged.newBuilder().setProductId("p1")
                .setAverageRating(4.5).setReviewCount(10).addAllStarCounts(List.of(0L, 0L, 1L, 3L, 6L)).build()));
        consumer.on(record("ecommerce.inventory.events", StockLevelChanged.newBuilder().setProductId("p1")
                .setWarehouseId("w1").setAvailable(7).build()));
        consumer.on(record("ecommerce.catalog.events", CategoryUpserted.newBuilder().setCategoryId("c1")
                .setName("Nam").setSlug("nam").build()));

        verify(readModel).onRatingChanged("p1", 4.5, 10, List.of(0L, 0L, 1L, 3L, 6L), OCCURRED);
        verify(readModel).onStockChanged("p1", "w1", 7, OCCURRED);
        verify(readModel).onCategoryChanged("c1", "Nam", "nam", OCCURRED);
    }

    @Test
    void otherEventsOnTheSameTopicsAreIgnored() {
        consumer.on(record("ecommerce.inventory.events", FlashSaleClaimed.newBuilder().setProductId("p1").build()));

        verifyNoInteractions(readModel);
    }

    private static ConsumerRecord<String, byte[]> record(String topic, Message payload) {
        EventEnvelope env = EventEnvelope.newBuilder().setId("e1").setOccurredAt(ts(OCCURRED))
                .setPayload(Any.pack(payload)).build();
        return new ConsumerRecord<>(topic, 0, 0, "k", env.toByteArray());
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }
}
