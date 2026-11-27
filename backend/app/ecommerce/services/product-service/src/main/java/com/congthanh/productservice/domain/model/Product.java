package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.productservice.domain.event.ProductEvent;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import com.congthanh.productservice.domain.support.Slugs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Aggregate Product: sản phẩm gốc ({@code parentId == null}) hoặc variant (đúng một cấp dưới sản phẩm gốc, ADR 0030).
 * Mọi thay đổi đi qua method có kiểm bất biến và ghi một {@link ProductEvent}; adapter persistence lưu aggregate rồi
 * ghi event vào outbox cùng transaction. Ràng buộc cần nhìn các aggregate khác (slug/SKU/GTIN unique, tổ hợp variant
 * không trùng) do use case kiểm trước, DB là chốt chặn cuối.
 */
public final class Product {

    private static final Pattern GTIN = Pattern.compile("^[0-9]{8,14}$");
    private static final int MAX_NAME = 255;
    private static final int MAX_SKU = 64;

    private final String id;
    private final String parentId;
    private String name;
    private String slug;
    private String sku;
    private String gtin;
    private String description;
    private Specifications specifications = Specifications.EMPTY;
    private Money price;
    private String supplier;
    private String ownerId;
    private Long brandId;
    private boolean featured;
    private ProductStatus status;
    private Long thumbnailId;
    private boolean hasVariant;
    private final String variantKey;
    private final Long version;
    private final List<ProductImage> images = new ArrayList<>();
    private final List<CategoryAssignment> categories = new ArrayList<>();
    /** Giá trị thông số theo id thuộc tính; thuộc tính MULTI_SELECT có thể có nhiều giá trị (ADR 0033). */
    private final Map<Long, List<AttributeValue>> attributes = new LinkedHashMap<>();
    private final List<OptionValue> optionValues = new ArrayList<>();
    private final List<OptionSelection> selections = new ArrayList<>();

    private final List<ProductEvent> events = new ArrayList<>();

    private Product(String id, String parentId, String variantKey, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.parentId = parentId;
        this.variantKey = variantKey;
        this.version = version;
    }

    /**
     * Thông tin tạo sản phẩm gốc. Ảnh là ảnh đã có trên media-service. {@code ownerId}: subject của SELLER tạo sản
     * phẩm, {@code null} là sản phẩm của sàn (chỉ ADMIN sửa).
     */
    public record Draft(String id, String name, String slug, String sku, String gtin, Money price, String description,
                        String supplier, Long brandId, boolean featured, List<String> categoryIds,
                        List<ProductImage> images, Long thumbnailId, String ownerId) {
    }

    /** Thông tin tạo variant; {@code name} trống thì ghép tên sản phẩm gốc với các giá trị đã chọn. */
    public record VariantDraft(String id, String name, String sku, String gtin, Money price, ProductStatus status) {
    }

    /** Tạo sản phẩm gốc ở trạng thái INACTIVE (bật bán bằng {@link #activate}). */
    public static Product create(Draft d) {
        Product p = new Product(d.id(), null, null, null);
        p.status = ProductStatus.INACTIVE;
        p.name = requireName(d.name());
        p.slug = requireSlug(d.slug());
        p.sku = normalizeSku(d.sku());
        p.gtin = normalizeGtin(d.gtin());
        p.price = d.price();
        p.description = d.description();
        p.supplier = blankToNull(d.supplier());
        p.ownerId = blankToNull(d.ownerId());
        p.brandId = d.brandId();
        p.featured = d.featured();
        p.replaceCategoriesInternal(d.categoryIds());
        Set<Long> seen = new HashSet<>();
        for (ProductImage i : d.images() == null ? List.<ProductImage>of() : d.images()) {
            if (!seen.add(i.mediaId())) {
                throw new BadRequestException("image " + i.mediaId() + " is listed twice");
            }
            p.images.add(i);
        }
        if (d.thumbnailId() != null) {
            p.setThumbnailInternal(d.thumbnailId());
        } else if (!p.images.isEmpty()) {
            p.thumbnailId = p.images.getFirst().mediaId();
        }
        p.changed();
        return p;
    }

    /**
     * Tạo variant dưới sản phẩm gốc này. {@code chosen}: đúng một giá trị trên mỗi trục, đã khai báo trên sản phẩm gốc
     * (xem {@link #declareOptionValue}). {@code existingAxes}: bộ trục của các variant đang có (rỗng nếu chưa có), mọi
     * variant phải dùng cùng bộ trục. Use case kiểm SKU và tổ hợp chưa tồn tại trước khi gọi.
     */
    public Product createVariant(VariantDraft d, List<OptionValue> chosen, Set<Long> existingAxes) {
        requireLive();
        if (parentId != null) {
            throw new BadRequestException("a variant cannot have variants of its own");
        }
        if (chosen == null || chosen.isEmpty()) {
            throw new BadRequestException("a variant needs at least one option");
        }
        Set<Long> axes = chosen.stream().map(OptionValue::optionId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (axes.size() != chosen.size()) {
            throw new BadRequestException("each option may be chosen only once");
        }
        if (!existingAxes.isEmpty() && !existingAxes.equals(axes)) {
            throw new BadRequestException("all variants of a product must use the same options " + existingAxes);
        }
        for (OptionValue v : chosen) {
            if (optionValues.stream().noneMatch(o -> o.id() == v.id())) {
                throw new BadRequestException("option value " + v.id() + " is not declared on product " + id);
            }
        }
        Product v = new Product(d.id(), id, variantKey(chosen), null);
        v.ownerId = ownerId;
        String defaultName = name + " " + chosen.stream().map(OptionValue::value).collect(Collectors.joining(" "));
        v.name = d.name() == null || d.name().isBlank() ? requireName(defaultName) : requireName(d.name());
        v.sku = normalizeSku(d.sku());
        if (v.sku == null) {
            throw new BadRequestException("sku is required for a variant");
        }
        v.gtin = normalizeGtin(d.gtin());
        v.price = d.price();
        if (d.status() == ProductStatus.DELETED) {
            throw new BadRequestException("cannot create a deleted variant");
        }
        v.status = d.status() == null ? ProductStatus.ACTIVE : d.status();
        for (int i = 0; i < chosen.size(); i++) {
            v.selections.add(new OptionSelection(chosen.get(i).optionId(), chosen.get(i).id(), i + 1));
        }
        if (!hasVariant) {
            hasVariant = true;
            changed();
        }
        v.changed();
        return v;
    }

    /** Id các giá trị đã chọn, sắp tăng dần, nối '-' (giống seed và unique index products_variant_key). */
    public static String variantKey(Collection<OptionValue> values) {
        return values.stream().map(OptionValue::id).sorted().map(String::valueOf).collect(Collectors.joining("-"));
    }

    /**
     * Trả giá trị trục đã khai báo (khớp không phân biệt hoa thường), hoặc khai báo mới với thứ tự tiếp theo.
     * Chỉ sản phẩm gốc mới khai báo giá trị trục.
     */
    public OptionValue declareOptionValue(long optionId, String value, LongSupplier newId) {
        requireLive();
        if (parentId != null) {
            throw new BadRequestException("option values are declared on the parent product");
        }
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            throw new BadRequestException("option value is required");
        }
        Optional<OptionValue> existing = optionValues.stream()
                .filter(o -> o.optionId() == optionId && o.value().equalsIgnoreCase(v)).findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        int order = optionValues.stream().filter(o -> o.optionId() == optionId)
                .mapToInt(OptionValue::displayOrder).max().orElse(0) + 1;
        OptionValue created = new OptionValue(newId.getAsLong(), optionId, v, order);
        optionValues.add(created);
        changed();
        return created;
    }

    // ------------------------------------------------------------------ sửa thông tin

    public void requireVersion(Long expected) {
        if (!Objects.equals(version, expected)) {
            throw new ConflictException("product was changed by someone else (version " + version + ")");
        }
    }

    public void rename(String newName) {
        requireLive();
        name = requireName(newName);
        changed();
    }

    public void changeSlug(String newSlug) {
        requireLive();
        slug = requireSlug(newSlug);
        changed();
    }

    public void changeSku(String newSku) {
        requireLive();
        String s = normalizeSku(newSku);
        if (s == null && parentId != null) {
            throw new BadRequestException("sku is required for a variant");
        }
        sku = s;
        changed();
    }

    public void changeGtin(String newGtin) {
        requireLive();
        gtin = normalizeGtin(newGtin);
        changed();
    }

    /** Đổi giá niêm yết; đang bán thì không được bỏ giá của sản phẩm không có variant. */
    public void reprice(Money newPrice) {
        requireLive();
        if (newPrice == null && status == ProductStatus.ACTIVE && !hasVariant) {
            throw new BadRequestException("an ACTIVE product needs a price");
        }
        price = newPrice;
        changed();
    }

    public void describe(String newDescription) {
        requireLive();
        description = newDescription;
        changed();
    }

    /** Thay toàn bộ thông số tự do (ADR 0033); chỉ sản phẩm gốc có, biến thể dùng chung của cha. */
    public void changeSpecifications(Specifications newSpecifications) {
        requireLive();
        if (parentId != null && newSpecifications != null && !newSpecifications.isEmpty()) {
            throw new BadRequestException("specifications belong to the parent product, not to a variant");
        }
        specifications = newSpecifications == null ? Specifications.EMPTY : newSpecifications;
        changed();
    }

    public void changeSupplier(String newSupplier) {
        requireLive();
        supplier = blankToNull(newSupplier);
        changed();
    }

    public void changeBrand(Long newBrandId) {
        requireLive();
        brandId = newBrandId;
        changed();
    }

    public void feature(boolean isFeatured) {
        requireLive();
        featured = isFeatured;
        changed();
    }

    // ------------------------------------------------------------------ danh mục

    public void replaceCategories(List<String> categoryIds) {
        requireLive();
        replaceCategoriesInternal(categoryIds);
        changed();
    }

    public void assignCategory(String categoryId) {
        requireLive();
        if (categories.stream().anyMatch(c -> c.categoryId().equals(categoryId))) {
            throw new ConflictException("product already has category " + categoryId);
        }
        int order = categories.stream().mapToInt(CategoryAssignment::displayOrder).max().orElse(0) + 1;
        categories.add(new CategoryAssignment(categoryId, order));
        changed();
    }

    public void removeCategory(String categoryId) {
        requireLive();
        if (categories.stream().noneMatch(c -> c.categoryId().equals(categoryId))) {
            throw new NotFoundException("product does not have category " + categoryId);
        }
        if (categories.size() == 1 && parentId == null) {
            throw new BadRequestException("a product needs at least one category");
        }
        categories.removeIf(c -> c.categoryId().equals(categoryId));
        changed();
    }

    // ------------------------------------------------------------------ ảnh

    /** Gắn ảnh; {@code asDefault} hoặc chưa có ảnh đại diện thì ảnh này thành ảnh đại diện. */
    public void addImage(ProductImage image, boolean asDefault) {
        requireLive();
        if (images.stream().anyMatch(i -> i.mediaId() == image.mediaId())) {
            throw new ConflictException("image " + image.mediaId() + " is already attached to this product");
        }
        images.add(image);
        if (asDefault || thumbnailId == null) {
            thumbnailId = image.mediaId();
        }
        changed();
    }

    /** Gỡ ảnh; gỡ ảnh đại diện thì ảnh đầu tiên còn lại (theo thứ tự hiển thị) thành ảnh đại diện. */
    public void removeImage(long mediaId) {
        requireLive();
        if (!images.removeIf(i -> i.mediaId() == mediaId)) {
            throw new NotFoundException("image " + mediaId + " is not an image of product " + id);
        }
        if (Objects.equals(thumbnailId, mediaId)) {
            thumbnailId = images.stream()
                    .min(Comparator.comparingInt(ProductImage::displayOrder).thenComparingLong(ProductImage::mediaId))
                    .map(ProductImage::mediaId).orElse(null);
        }
        changed();
    }

    public void setThumbnail(long mediaId) {
        requireLive();
        setThumbnailInternal(mediaId);
        changed();
    }

    // ------------------------------------------------------------------ thuộc tính

    /**
     * Gán giá trị cho một thuộc tính chưa có trên sản phẩm; đã có thì 409 (dùng {@link #changeAttribute}).
     * {@code values} là kết quả {@link ProductAttribute#valuesOf}: cùng một thuộc tính, đã chuẩn hoá và bỏ trùng.
     */
    public void addAttribute(List<AttributeValue> values) {
        requireLive();
        long attributeId = requireSameAttribute(values);
        if (attributes.containsKey(attributeId)) {
            throw new ConflictException("product already has a value for attribute " + attributeId);
        }
        attributes.put(attributeId, List.copyOf(values));
        changed();
    }

    /** Thay toàn bộ giá trị của một thuộc tính đã có trên sản phẩm; chưa có thì 404. */
    public void changeAttribute(List<AttributeValue> values) {
        requireLive();
        long attributeId = requireSameAttribute(values);
        if (!attributes.containsKey(attributeId)) {
            throw new NotFoundException("attribute value not found");
        }
        attributes.put(attributeId, List.copyOf(values));
        changed();
    }

    public void removeAttribute(long attributeId) {
        requireLive();
        if (attributes.remove(attributeId) == null) {
            throw new NotFoundException("attribute value not found");
        }
        changed();
    }

    private static long requireSameAttribute(List<AttributeValue> values) {
        if (values == null || values.isEmpty()) {
            throw new BadRequestException("at least one attribute value is required");
        }
        long attributeId = values.getFirst().attributeId();
        if (values.stream().anyMatch(v -> v.attributeId() != attributeId)) {
            throw new IllegalStateException("values must belong to one attribute");
        }
        return attributeId;
    }

    // ------------------------------------------------------------------ vòng đời

    /**
     * Bật bán. Sản phẩm không có variant cần giá; sản phẩm có variant cần ít nhất một variant đang bán có giá
     * ({@code hasSellableVariant}, use case đọc từ các variant).
     */
    public void activate(boolean hasSellableVariant) {
        requireLive();
        if (!hasVariant && price == null) {
            throw new BadRequestException("a product needs a price before it can be ACTIVE");
        }
        if (hasVariant && !hasSellableVariant) {
            throw new BadRequestException("a product with variants needs an ACTIVE priced variant before it can be ACTIVE");
        }
        if (status != ProductStatus.ACTIVE) {
            status = ProductStatus.ACTIVE;
            changed();
        }
    }

    public void deactivate() {
        requireLive();
        if (status != ProductStatus.INACTIVE) {
            status = ProductStatus.INACTIVE;
            changed();
        }
    }

    /** Xoá mềm (slug, GTIN, tổ hợp variant dùng lại được; SKU thì không). Xoá lần hai thì 404. */
    public void delete() {
        requireLive();
        status = ProductStatus.DELETED;
        events.removeIf(e -> e instanceof ProductEvent.ProductChanged);
        events.add(new ProductEvent.ProductDeleted(id));
    }

    public boolean isSellable() {
        return status == ProductStatus.ACTIVE && price != null;
    }

    /** Lấy và xoá các event chưa phát; adapter persistence gọi khi lưu. */
    public List<ProductEvent> pullEvents() {
        List<ProductEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    // ------------------------------------------------------------------ nội bộ

    private void requireLive() {
        if (status == ProductStatus.DELETED) {
            throw new NotFoundException("product not found: " + id);
        }
    }

    private void changed() {
        boolean recorded = events.stream().anyMatch(e -> e instanceof ProductEvent.ProductChanged);
        if (!recorded && status != ProductStatus.DELETED) {
            events.add(new ProductEvent.ProductChanged(id));
        }
    }

    private void replaceCategoriesInternal(List<String> categoryIds) {
        List<String> ids = categoryIds == null ? List.of()
                : categoryIds.stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        if (ids.isEmpty() && parentId == null) {
            throw new BadRequestException("a product needs at least one category");
        }
        categories.clear();
        for (int i = 0; i < ids.size(); i++) {
            categories.add(new CategoryAssignment(ids.get(i), i + 1));
        }
    }

    private void setThumbnailInternal(long mediaId) {
        if (images.stream().noneMatch(i -> i.mediaId() == mediaId)) {
            throw new BadRequestException("thumbnail " + mediaId + " is not an image of this product");
        }
        thumbnailId = mediaId;
    }

    private static String requireName(String raw) {
        String n = raw == null ? "" : raw.strip();
        if (n.isEmpty()) {
            throw new BadRequestException("name is required");
        }
        if (n.length() > MAX_NAME) {
            throw new BadRequestException("name is longer than " + MAX_NAME + " characters");
        }
        return n;
    }

    private static String requireSlug(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (!Slugs.isValid(s)) {
            throw new BadRequestException("slug must match " + Slugs.FORMAT.pattern() + ": " + raw);
        }
        return s;
    }

    private static String normalizeSku(String raw) {
        String s = blankToNull(raw);
        if (s != null && s.length() > MAX_SKU) {
            throw new BadRequestException("sku is longer than " + MAX_SKU + " characters");
        }
        return s;
    }

    private static String normalizeGtin(String raw) {
        String g = blankToNull(raw);
        if (g != null && !GTIN.matcher(g).matches()) {
            throw new BadRequestException("gtin must be 8 to 14 digits: " + g);
        }
        return g;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // ------------------------------------------------------------------ dựng lại từ storage

    /** Trạng thái lưu trữ của aggregate; adapter persistence dựng từ entity. Không kiểm tra lại, không phát event. */
    public record State(String id, String parentId, String name, String slug, String sku, String gtin,
                        String description, Money price, String supplier, Long brandId, boolean featured,
                        ProductStatus status, Long thumbnailId, boolean hasVariant, String variantKey, Long version,
                        List<ProductImage> images, List<CategoryAssignment> categories, List<AttributeValue> attributes,
                        List<OptionValue> optionValues, List<OptionSelection> selections, String ownerId,
                        Specifications specifications) {
    }

    public static Product rehydrate(State s) {
        Product p = new Product(s.id(), s.parentId(), s.variantKey(), s.version());
        p.name = s.name();
        p.slug = s.slug();
        p.sku = s.sku();
        p.gtin = s.gtin();
        p.description = s.description();
        p.specifications = s.specifications() == null ? Specifications.EMPTY : s.specifications();
        p.price = s.price();
        p.supplier = s.supplier();
        p.ownerId = s.ownerId();
        p.brandId = s.brandId();
        p.featured = s.featured();
        p.status = s.status();
        p.thumbnailId = s.thumbnailId();
        p.hasVariant = s.hasVariant();
        p.images.addAll(s.images());
        p.categories.addAll(s.categories());
        for (AttributeValue a : s.attributes()) {
            p.attributes.computeIfAbsent(a.attributeId(), k -> new ArrayList<>()).add(a);
        }
        p.optionValues.addAll(s.optionValues());
        p.selections.addAll(s.selections());
        return p;
    }

    public String id() { return id; }
    public String parentId() { return parentId; }
    public boolean isVariant() { return parentId != null; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String sku() { return sku; }
    public String gtin() { return gtin; }
    public String description() { return description; }
    public Specifications specifications() { return specifications; }
    public Money price() { return price; }
    public String supplier() { return supplier; }
    public String ownerId() { return ownerId; }

    /** Người bán {@code subject} có sở hữu sản phẩm không; sản phẩm của sàn ({@code ownerId == null}) không thuộc ai. */
    public boolean isOwnedBy(String subject) {
        return ownerId != null && ownerId.equals(subject);
    }
    public Long brandId() { return brandId; }
    public boolean featured() { return featured; }
    public ProductStatus status() { return status; }
    public Long thumbnailId() { return thumbnailId; }
    public boolean hasVariant() { return hasVariant; }
    public String variantKey() { return variantKey; }
    public Long version() { return version; }
    public List<ProductImage> images() { return List.copyOf(images); }
    public List<CategoryAssignment> categories() { return List.copyOf(categories); }
    /** Mọi giá trị thông số, gom theo thuộc tính (thứ tự thêm vào). */
    public List<AttributeValue> attributes() { return attributes.values().stream().flatMap(List::stream).toList(); }
    public List<OptionValue> optionValues() { return List.copyOf(optionValues); }
    public List<OptionSelection> selections() { return List.copyOf(selections); }
}
