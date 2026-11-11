package com.congthanh.productservice.adapter.out.messaging;

import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.productservice.application.event.ProductChanged;
import com.congthanh.productservice.grpc.ProductDeleted;
import com.congthanh.productservice.grpc.ProductUpserted;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ghi event công khai vào outbox TRƯỚC commit, nên event và dữ liệu cùng sống hoặc cùng chết. */
@Component
class ProductOutboxListener {

    static final String AGGREGATE = "product";

    private final OutboxWriter outbox;
    private final String currency;

    ProductOutboxListener(OutboxWriter outbox, @Value("${product.currency:USD}") String currency) {
        this.outbox = outbox;
        this.currency = currency;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(ProductChanged e) {
        if (e.deleted()) {
            outbox.write(AGGREGATE, e.productId(), ProductDeleted.newBuilder().setProductId(e.productId()).build());
            return;
        }
        var b = ProductUpserted.newBuilder()
                .setProductId(e.productId())
                .setSku(nz(e.sku())).setName(nz(e.name())).setSlug(nz(e.slug()))
                .setCategoryId(e.categoryIds().isEmpty() ? "" : e.categoryIds().getFirst())
                .setStatus(nz(e.status()));
        if (e.price() != null) {
            BigDecimal a = e.price().setScale(9, RoundingMode.HALF_EVEN);
            BigDecimal units = a.setScale(0, RoundingMode.DOWN);
            b.setPrice(Money.newBuilder().setCurrencyCode(currency).setUnits(units.longValueExact())
                    .setNanos(a.subtract(units).movePointRight(9).intValueExact()));
        }
        outbox.write(AGGREGATE, e.productId(), b.build());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
