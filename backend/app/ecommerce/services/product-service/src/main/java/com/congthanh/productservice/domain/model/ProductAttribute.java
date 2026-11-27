package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.productservice.domain.model.enums.AttributeDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aggregate định nghĩa thuộc tính mô tả (EAV có kiểu, ADR 0030, 0033). {@code code} là khoá ổn định dùng trong URL lọc
 * và chỉ mục tìm kiếm; thuộc tính lọc được bắt buộc có code. Không đổi kiểu khi đã có giá trị (giá trị cũ sẽ lệch kiểu).
 * {@code options} là danh sách giá trị cho phép (chỉ TEXT/MULTI_SELECT); rỗng nghĩa là nhập tự do.
 */
public final class ProductAttribute {

    public static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    /** Trần số giá trị của một thuộc tính MULTI_SELECT trên một sản phẩm, giữ trang chi tiết và bộ lọc gọn. */
    public static final int MAX_VALUES_PER_PRODUCT = 20;
    /** Trần số giá trị cho phép của một thuộc tính. */
    public static final int MAX_OPTIONS = 200;
    private static final int MAX_OPTION_LENGTH = 255;

    private final long id;
    private String code;
    private String name;
    private AttributeDataType dataType;
    private String unit;
    private boolean filterable;
    private boolean searchable;
    private int displayOrder;
    private Long groupId;
    private List<String> options = List.of();

    private ProductAttribute(long id) {
        this.id = id;
    }

    /** Thông số của một định nghĩa thuộc tính, dùng cho cả tạo mới và sửa. {@code options} null coi như rỗng. */
    public record Spec(String code, String name, AttributeDataType dataType, String unit, boolean filterable,
                       boolean searchable, int displayOrder, Long groupId, List<String> options) {
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

    /**
     * Chuẩn hoá các giá trị của thuộc tính này trên một sản phẩm. Thuộc tính không phải MULTI_SELECT nhận đúng một giá
     * trị; giá trị trùng (không phân biệt hoa thường) bị gộp; có danh sách cho phép thì giá trị phải nằm trong đó và
     * được viết lại đúng chính tả của danh sách.
     */
    public List<AttributeValue> valuesOf(List<String> raws) {
        if (raws == null || raws.isEmpty()) {
            throw new BadRequestException(name + ": at least one value is required");
        }
        if (!dataType.multiValued() && raws.size() > 1) {
            throw new BadRequestException(name + ": accepts a single value");
        }
        if (raws.size() > MAX_VALUES_PER_PRODUCT) {
            throw new BadRequestException(name + ": at most " + MAX_VALUES_PER_PRODUCT + " values");
        }
        Map<String, String> allowed = new LinkedHashMap<>();
        for (String o : options) {
            allowed.put(o.toLowerCase(Locale.ROOT), o);
        }
        List<AttributeValue> out = new ArrayList<>(raws.size());
        Set<String> seen = new HashSet<>();
        for (String raw : raws) {
            AttributeDataType.Normalized n;
            try {
                n = dataType.normalize(raw);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(name + ": " + e.getMessage());
            }
            String key = n.value().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            String value = n.value();
            if (!allowed.isEmpty()) {
                value = allowed.get(key);
                if (value == null) {
                    throw new BadRequestException(name + ": " + n.value() + " is not an allowed value");
                }
            }
            out.add(new AttributeValue(id, value, n.number()));
        }
        return List.copyOf(out);
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
        List<String> opts = normalizeOptions(spec.options(), type);
        this.name = n;
        this.code = c;
        this.dataType = type;
        this.unit = spec.unit() == null || spec.unit().isBlank() ? null : spec.unit().strip();
        this.filterable = spec.filterable();
        this.searchable = spec.searchable();
        this.displayOrder = spec.displayOrder();
        this.groupId = spec.groupId();
        this.options = opts;
    }

    private static List<String> normalizeOptions(List<String> raw, AttributeDataType type) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (type != AttributeDataType.TEXT && type != AttributeDataType.MULTI_SELECT) {
            throw new BadRequestException("allowed values are only supported for TEXT and MULTI_SELECT attributes");
        }
        if (raw.size() > MAX_OPTIONS) {
            throw new BadRequestException("at most " + MAX_OPTIONS + " allowed values");
        }
        List<String> out = new ArrayList<>(raw.size());
        Set<String> seen = new HashSet<>();
        for (String o : raw) {
            String v = o == null ? "" : o.strip();
            if (v.isEmpty()) {
                throw new BadRequestException("allowed values must not be blank");
            }
            if (v.length() > MAX_OPTION_LENGTH) {
                throw new BadRequestException("allowed value is longer than " + MAX_OPTION_LENGTH + " characters: " + v);
            }
            if (!seen.add(v.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("duplicate allowed value: " + v);
            }
            out.add(v);
        }
        return List.copyOf(out);
    }

    /** Dựng lại từ storage, không kiểm tra lại. */
    public static ProductAttribute rehydrate(long id, String code, String name, AttributeDataType dataType, String unit,
                                             boolean filterable, boolean searchable, int displayOrder, Long groupId,
                                             List<String> options) {
        ProductAttribute a = new ProductAttribute(id);
        a.code = code;
        a.name = Objects.requireNonNull(name, "name");
        a.dataType = dataType == null ? AttributeDataType.TEXT : dataType;
        a.unit = unit;
        a.filterable = filterable;
        a.searchable = searchable;
        a.displayOrder = displayOrder;
        a.groupId = groupId;
        a.options = options == null ? List.of() : List.copyOf(options);
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
    public Long groupId() { return groupId; }
    public List<String> options() { return options; }
}
