package com.congthanh.productservice.adapter.in.messaging;

import com.congthanh.catalogservice.grpc.BrandUpserted;
import com.congthanh.catalogservice.grpc.CategoryUpserted;
import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.pricing.v1.PriceChanged;
import com.congthanh.inventoryservice.grpc.StockLevelChanged;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.productservice.application.port.in.ProductReadModelUseCase;
import com.congthanh.productservice.grpc.ProductDeleted;
import com.congthanh.productservice.grpc.ProductUpserted;
import com.congthanh.reviewservice.grpc.ProductRatingChanged;
import com.google.protobuf.Timestamp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
class ProductReadModelConsumer {

    static final String GROUP = "product-read-model";

    private final ProductReadModelUseCase readModel;

    ProductReadModelConsumer(ProductReadModelUseCase readModel) {
        this.readModel = readModel;
    }

    @KafkaListener(groupId = GROUP, concurrency = "${product.read-model.consumer-concurrency:3}",
            topics = {"ecommerce.product.events", "ecommerce.pricing.events", "ecommerce.review.events",
                    "ecommerce.inventory.events", "ecommerce.catalog.events"})
    void on(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        Instant at = instant(env.getOccurredAt());

        EventEnvelopes.unpack(env, ProductUpserted.class).ifPresent(e -> readModel.refresh(e.getProductId()));
        EventEnvelopes.unpack(env, ProductDeleted.class).ifPresent(e -> readModel.refresh(e.getProductId()));

        EventEnvelopes.unpack(env, PriceChanged.class).ifPresent(e -> readModel.onPriceChanged(e.getProductId(),
                e.getPriceList(), amount(e.getNewPrice()), e.getNewPrice().getCurrencyCode(),
                e.hasEffectiveAt() ? instant(e.getEffectiveAt()) : at));

        EventEnvelopes.unpack(env, ProductRatingChanged.class).ifPresent(e -> readModel.onRatingChanged(
                e.getProductId(), e.getAverageRating(), e.getReviewCount(), e.getStarCountsList(), at));

        EventEnvelopes.unpack(env, StockLevelChanged.class).ifPresent(e -> readModel.onStockChanged(
                e.getProductId(), e.getWarehouseId(), e.getAvailable(), at));

        EventEnvelopes.unpack(env, CategoryUpserted.class).ifPresent(e -> readModel.onCategoryChanged(
                e.getCategoryId(), e.getName(), e.getSlug(), e.hasUpdatedAt() ? instant(e.getUpdatedAt()) : at));

        EventEnvelopes.unpack(env, BrandUpserted.class).ifPresent(e -> readModel.onBrandChanged(
                e.getBrandId(), e.getName(), e.getSlug(), at));
    }

    static BigDecimal amount(Money m) {
        BigDecimal a = BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNanos(), 9)).stripTrailingZeros();
        return a.scale() < 2 ? a.setScale(2) : a;
    }

    private static Instant instant(Timestamp t) {
        return Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
    }
}
