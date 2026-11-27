package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Thông số tự do của sản phẩm, chỉ để hiển thị (ADR 0033): nhóm có tên (hoặc không tên) chứa các dòng tên/giá trị.
 * Thông số cần lọc, so sánh hay kiểm giá trị phải là thuộc tính EAV ({@link ProductAttribute}), không đặt ở đây.
 */
public record Specifications(List<Group> groups) {

    public static final Specifications EMPTY = new Specifications(List.of());
    public static final int MAX_GROUPS = 30;
    public static final int MAX_ITEMS = 300;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_VALUE_LENGTH = 1_000;

    /** {@code name} null = nhóm không tiêu đề. */
    public record Group(String name, List<Item> items) {
        public Group {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record Item(String name, String value) {
    }

    public Specifications {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /**
     * Chuẩn hoá và kiểm dữ liệu từ ngoài: bỏ khoảng trắng thừa, bỏ nhóm rỗng; tên/giá trị không được rỗng, không quá dài,
     * tên dòng không trùng trong cùng nhóm, tên nhóm không trùng. Sai thì 400 nêu rõ vị trí.
     */
    public static Specifications of(List<Group> raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY;
        }
        if (raw.size() > MAX_GROUPS) {
            throw new BadRequestException("specifications have at most " + MAX_GROUPS + " groups");
        }
        List<Group> groups = new ArrayList<>(raw.size());
        Set<String> groupNames = new HashSet<>();
        int total = 0;
        for (int g = 0; g < raw.size(); g++) {
            Group group = raw.get(g);
            String name = optional(group == null ? null : group.name(), MAX_NAME_LENGTH, "group " + (g + 1) + " name");
            if (name != null && !groupNames.add(name.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("duplicate specification group: " + name);
            }
            List<Item> items = items(group == null ? List.of() : group.items(), g + 1);
            total += items.size();
            if (total > MAX_ITEMS) {
                throw new BadRequestException("specifications have at most " + MAX_ITEMS + " rows");
            }
            if (!items.isEmpty()) {
                groups.add(new Group(name, items));
            }
        }
        return new Specifications(groups);
    }

    private static List<Item> items(List<Item> raw, int groupNo) {
        List<Item> out = new ArrayList<>(raw.size());
        Set<String> names = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            Item item = raw.get(i);
            String where = "group " + groupNo + " row " + (i + 1);
            String name = required(item == null ? null : item.name(), MAX_NAME_LENGTH, where + " name");
            String value = required(item == null ? null : item.value(), MAX_VALUE_LENGTH, where + " value");
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("duplicate specification row in group " + groupNo + ": " + name);
            }
            out.add(new Item(name, value));
        }
        return out;
    }

    private static String required(String raw, int max, String field) {
        String v = optional(raw, max, field);
        if (v == null) {
            throw new BadRequestException(field + " is required");
        }
        return v;
    }

    private static String optional(String raw, int max, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.strip();
        if (v.length() > max) {
            throw new BadRequestException(field + " is longer than " + max + " characters");
        }
        return v;
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }
}
