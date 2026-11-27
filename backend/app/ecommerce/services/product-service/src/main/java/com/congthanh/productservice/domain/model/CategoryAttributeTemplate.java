package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Mẫu thông số của một danh mục (ADR 0033): thuộc tính nào áp dụng cho sản phẩm trong danh mục, có bắt buộc không và
 * thứ tự trong form của seller. Danh mục thuộc catalog-service nên chỉ giữ id.
 */
public record CategoryAttributeTemplate(String categoryId, List<Item> items) {

    /** Trần số thuộc tính trong một mẫu. */
    public static final int MAX_ITEMS = 100;
    /** Độ dài id danh mục của catalog-service (cột category_id varchar(32)). */
    private static final int MAX_CATEGORY_ID_LENGTH = 32;

    public record Item(long attributeId, boolean required, int displayOrder) {
    }

    public CategoryAttributeTemplate {
        Objects.requireNonNull(categoryId, "categoryId");
        items = items == null ? List.of() : List.copyOf(items);
        if (categoryId.isBlank()) {
            throw new BadRequestException("categoryId is required");
        }
        if (categoryId.length() > MAX_CATEGORY_ID_LENGTH) {
            throw new BadRequestException("categoryId is longer than " + MAX_CATEGORY_ID_LENGTH + " characters");
        }
        if (items.size() > MAX_ITEMS) {
            throw new BadRequestException("a category template has at most " + MAX_ITEMS + " attributes");
        }
        Set<Long> seen = new HashSet<>();
        for (Item i : items) {
            if (!seen.add(i.attributeId())) {
                throw new BadRequestException("attribute " + i.attributeId() + " appears twice in the template");
            }
        }
    }
}
