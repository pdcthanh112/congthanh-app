package com.congthanh.productservice.application.port.out;

import com.congthanh.productservice.domain.model.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bản chụp đầy đủ của một sản phẩm đọc trong một transaction: nguồn chung của read model Mongo và event
 * ProductUpserted, để hai nơi luôn khớp nhau (ADR 0030). Sản phẩm gốc có {@code variants} (chưa xoá); variant có
 * {@code options} của chính nó.
 */
public record ProductSnapshot(String id, String parentId, String name, String slug, String sku, String gtin,
                              String description, ProductStatus status, boolean featured, boolean hasVariant,
                              String supplier, Long brandId, BigDecimal price, String currency, Long thumbnailId,
                              List<Image> images, List<String> categoryIds, List<Attribute> attributes,
                              List<Option> options, List<ProductSnapshot> variants) {

    public ProductSnapshot {
        images = List.copyOf(images);
        categoryIds = List.copyOf(categoryIds);
        attributes = List.copyOf(attributes);
        options = List.copyOf(options);
        variants = List.copyOf(variants);
    }

    public record Image(long id, String url, int displayOrder) {
    }

    /** {@code code} null nếu thuộc tính chưa có code; {@code number} chỉ có với thuộc tính NUMBER. */
    public record Attribute(String code, String name, String value, BigDecimal number, String unit,
                            boolean filterable, boolean searchable, int displayOrder) {
    }

    public record Option(long optionId, String optionName, long valueId, String value, int valueOrder, int displayOrder) {
    }
}
