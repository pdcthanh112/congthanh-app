package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.productservice.application.port.out.ProductSnapshot;
import com.congthanh.productservice.grpc.AttributeValue;
import com.congthanh.productservice.grpc.OptionValue;
import com.congthanh.productservice.grpc.ProductUpserted;
import com.congthanh.productservice.grpc.VariantSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Bản chụp sản phẩm -> ProductUpserted (protobuf) cho outbox. */
final class ProductEventMapper {

    private ProductEventMapper() {
    }

    static ProductUpserted toProto(ProductSnapshot s) {
        var b = ProductUpserted.newBuilder()
                .setProductId(s.id())
                .setSku(nz(s.sku()))
                .setName(nz(s.name()))
                .setSlug(nz(s.slug()))
                .setCategoryId(s.categoryIds().isEmpty() ? "" : s.categoryIds().getFirst())
                .addAllCategoryIds(s.categoryIds())
                .setStatus(s.status() == null ? "" : s.status().name())
                .setParentId(nz(s.parentId()))
                .setHasVariant(s.hasVariant())
                .setFeatured(s.featured())
                .setBrandId(s.brandId() == null ? 0 : s.brandId());
        Money price = money(s.id(), s.price(), s.currency());
        if (price != null) {
            b.setPrice(price);
        }
        for (ProductSnapshot.Attribute a : s.attributes()) {
            var ab = AttributeValue.newBuilder()
                    .setCode(nz(a.code()))
                    .setName(nz(a.name()))
                    .setValue(nz(a.value()))
                    .setUnit(nz(a.unit()))
                    .setFilterable(a.filterable())
                    .setSearchable(a.searchable());
            if (a.number() != null) {
                ab.setNumber(a.number().doubleValue());
            }
            b.addAttributes(ab);
        }
        for (ProductSnapshot.Option o : s.options()) {
            b.addOptions(option(o));
        }
        for (ProductSnapshot v : s.variants()) {
            var vb = VariantSummary.newBuilder()
                    .setProductId(v.id())
                    .setSku(nz(v.sku()))
                    .setStatus(v.status() == null ? "" : v.status().name());
            Money vp = money(v.id(), v.price(), v.currency());
            if (vp != null) {
                vb.setPrice(vp);
            }
            for (ProductSnapshot.Option o : v.options()) {
                vb.addOptions(option(o));
            }
            b.addVariants(vb);
        }
        return b.build();
    }

    private static OptionValue option(ProductSnapshot.Option o) {
        return OptionValue.newBuilder()
                .setOptionId(o.optionId())
                .setOptionName(nz(o.optionName()))
                .setValueId(o.valueId())
                .setValue(nz(o.value()))
                .build();
    }

    /** Có giá mà thiếu currency là lỗi dữ liệu (DB đã chặn); không đoán currency (ADR 0029). */
    static Money money(String productId, BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalStateException("product " + productId + " has a price but no currency");
        }
        BigDecimal a = amount.setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = a.setScale(0, RoundingMode.DOWN);
        return Money.newBuilder()
                .setCurrencyCode(currency)
                .setUnits(units.longValueExact())
                .setNanos(a.subtract(units).movePointRight(9).intValueExact())
                .build();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
