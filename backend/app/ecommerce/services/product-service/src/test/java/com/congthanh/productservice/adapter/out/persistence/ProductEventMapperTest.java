package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.productservice.application.port.out.ProductSnapshot;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import com.congthanh.productservice.grpc.ProductUpserted;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductEventMapperTest {

    @Test
    void parentSnapshotCarriesAttributesAndVariantsWithOptions() {
        var black = new ProductSnapshot.Option(1L, "Color", 10L, "Black", 1, 1);
        var variant = snapshot("v1", "p1", "SKU-1", new BigDecimal("25000000.5"), "VND", List.of(), List.of(black), List.of());
        var parent = new ProductSnapshot("p1", null, "Iphone", "iphone", null, null, null, ProductStatus.ACTIVE, false, true,
                null, 7L, null, null, null, List.of(), List.of("c1", "c2"),
                List.of(new ProductSnapshot.Attribute("pages", "Pages", "225", new BigDecimal("225"), null, true, false, 0)),
                List.of(), List.of(variant));

        ProductUpserted e = ProductEventMapper.toProto(parent);

        assertThat(e.getCategoryId()).isEqualTo("c1");
        assertThat(e.getCategoryIdsList()).containsExactly("c1", "c2");
        assertThat(e.getBrandId()).isEqualTo(7L);
        assertThat(e.getParentId()).isEmpty();
        assertThat(e.hasPrice()).isFalse();
        assertThat(e.getAttributes(0).getNumber()).isEqualTo(225.0);
        assertThat(e.getAttributes(0).getFilterable()).isTrue();
        assertThat(e.getVariants(0).getPrice().getUnits()).isEqualTo(25_000_000L);
        assertThat(e.getVariants(0).getPrice().getNanos()).isEqualTo(500_000_000);
        assertThat(e.getVariants(0).getOptions(0).getValue()).isEqualTo("Black");
    }

    @Test
    void priceWithoutCurrencyIsAProgrammingError() {
        assertThatThrownBy(() -> ProductEventMapper.toProto(snapshot("p1", null, "S", BigDecimal.ONE, null, List.of(), List.of(),
                List.of()))).isInstanceOf(IllegalStateException.class);
    }

    private static ProductSnapshot snapshot(String id, String parentId, String sku, BigDecimal price, String currency,
                                            List<String> categories, List<ProductSnapshot.Option> options,
                                            List<ProductSnapshot> variants) {
        return new ProductSnapshot(id, parentId, id, null, sku, null, null, ProductStatus.ACTIVE, false, false, null, null,
                price, currency, null, List.of(), categories, List.of(), options, variants);
    }
}
