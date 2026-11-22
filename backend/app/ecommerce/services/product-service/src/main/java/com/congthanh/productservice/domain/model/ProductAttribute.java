package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.productservice.domain.model.enums.AttributeDataType;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aggregate định nghĩa thuộc tính mô tả (EAV có kiểu, ADR 0030). {@code code} là khoá ổn định dùng trong URL lọc và
 * chỉ mục tìm kiếm; thuộc tính lọc được bắt buộc có code. Không đổi kiểu khi đã có giá trị (giá trị cũ sẽ lệch kiểu).
 */
public final class ProductAttribute {

    public static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    private final long id;
    private String code;
    private String name;
    private AttributeDataType dataType;
    private String unit;
    private boolean filterable;
    private boolean searchable;
    private int displayOrder;

    private ProductAttribute(long id) {
        this.id = id;
    }

    /** Thông số của một định nghĩa thuộc tính, dùng cho cả tạo mới và sửa. */
    public record Spec(String code, String name, AttributeDataType dataType, String unit, boolean filterable,
                       boolean searchable, int displayOrder) {
    }

    public static ProductAttribute define(long id, Spec spec) {
        ProductAttribute a = new ProductAttribute(id);
        a.apply(spec, false);
        return a;
    }

    /** @param hasValues thuộc tính đã có giá trị trên sản phẩm nào chưa (quyết định có được đổi kiểu không) */
    public void redefine(Spec spec, boolean hasValues) {
        apply(spec, hasValues);
    }

    /** Chuẩn hoá giá trị thô theo kiểu của thuộc tính; sai kiểu thì 400 kèm tên thuộc tính. */
    public AttributeValue valueOf(String raw) {
        try {
            AttributeDataType.Normalized n = dataType.normalize(raw);
            return new AttributeValue(id, n.value(), n.number());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(name + ": " + e.getMessage());
        }
    }

    private void apply(Spec spec, boolean hasValues) {
        String n = spec.name() == null ? "" : spec.name().strip();
        if (n.isEmpty()) {
            throw new BadRequestException("name is required");
        }
        String c = spec.code() == null || spec.code().isBlank() ? null : spec.code().strip().toLowerCase(Locale.ROOT);
        if (c != null && !CODE.matcher(c).matches()) {
            throw new BadRequestException("code must match " + CODE.pattern());
        }
        if (spec.filterable() && c == null) {
            throw new BadRequestException("a filterable attribute needs a code");
        }
        AttributeDataType type = spec.dataType() == null ? AttributeDataType.TEXT : spec.dataType();
        if (hasValues && dataType != null && type != dataType) {
            throw new ConflictException("cannot change data type of an attribute that already has values");
        }
        this.name = n;
        this.code = c;
        this.dataType = type;
        this.unit = spec.unit() == null || spec.unit().isBlank() ? null : spec.unit().strip();
        this.filterable = spec.filterable();
        this.searchable = spec.searchable();
        this.displayOrder = spec.displayOrder();
    }

    /** Dựng lại từ storage, không kiểm tra lại. */
    public static ProductAttribute rehydrate(long id, String code, String name, AttributeDataType dataType, String unit,
                                             boolean filterable, boolean searchable, int displayOrder) {
        ProductAttribute a = new ProductAttribute(id);
        a.code = code;
        a.name = Objects.requireNonNull(name, "name");
        a.dataType = dataType == null ? AttributeDataType.TEXT : dataType;
        a.unit = unit;
        a.filterable = filterable;
        a.searchable = searchable;
        a.displayOrder = displayOrder;
        return a;
    }

    public long id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public AttributeDataType dataType() { return dataType; }
    public String unit() { return unit; }
    public boolean filterable() { return filterable; }
    public boolean searchable() { return searchable; }
    public int displayOrder() { return displayOrder; }
}
