package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;

import java.util.Objects;

/** Nhóm hiển thị của thông số sản phẩm ("Màn hình", "Pin"); trang chi tiết xếp nhóm theo {@code displayOrder} (ADR 0033). */
public final class AttributeGroup {

    private static final int MAX_NAME_LENGTH = 255;

    private final long id;
    private String name;
    private int displayOrder;

    private AttributeGroup(long id) {
        this.id = id;
    }

    public static AttributeGroup define(long id, String name, int displayOrder) {
        AttributeGroup g = new AttributeGroup(id);
        g.rename(name, displayOrder);
        return g;
    }

    /** Đổi tên và thứ tự; tên rỗng hoặc quá dài thì 400. */
    public void rename(String name, int displayOrder) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            throw new BadRequestException("group name is required");
        }
        if (n.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("group name is longer than " + MAX_NAME_LENGTH + " characters");
        }
        this.name = n;
        this.displayOrder = displayOrder;
    }

    /** Dựng lại từ storage, không kiểm tra lại. */
    public static AttributeGroup rehydrate(long id, String name, int displayOrder) {
        AttributeGroup g = new AttributeGroup(id);
        g.name = Objects.requireNonNull(name, "name");
        g.displayOrder = displayOrder;
        return g;
    }

    public long id() { return id; }
    public String name() { return name; }
    public int displayOrder() { return displayOrder; }
}
