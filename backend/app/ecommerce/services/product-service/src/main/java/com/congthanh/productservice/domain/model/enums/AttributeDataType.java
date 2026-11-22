package com.congthanh.productservice.domain.model.enums;

import java.math.BigDecimal;
import java.util.Locale;

/** Kiểu dữ liệu của thuộc tính mô tả: quyết định cách chuẩn hoá giá trị và cách lọc (khớp từ / theo khoảng). */
public enum AttributeDataType {
    TEXT,
    NUMBER,
    BOOLEAN;

    /** Giá trị đã chuẩn hoá: chuỗi hiển thị và (với NUMBER) số để lọc theo khoảng. */
    public record Normalized(String value, BigDecimal number) {
    }

    /** @throws IllegalArgumentException nếu giá trị không hợp kiểu */
    public Normalized normalize(String raw) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("value is required");
        }
        return switch (this) {
            case TEXT -> new Normalized(v, null);
            case NUMBER -> {
                BigDecimal n;
                try {
                    n = new BigDecimal(v.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("value must be a number: " + v);
                }
                yield new Normalized(n.stripTrailingZeros().toPlainString(), n);
            }
            case BOOLEAN -> switch (v.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "1", "có" -> new Normalized("true", null);
                case "false", "no", "0", "không" -> new Normalized("false", null);
                default -> throw new IllegalArgumentException("value must be true or false: " + v);
            };
        };
    }
}
