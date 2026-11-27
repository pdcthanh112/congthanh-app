package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.domain.event.ProductEvent;
import com.congthanh.productservice.domain.model.AttributeValue;
import com.congthanh.productservice.domain.model.CategoryAssignment;
import com.congthanh.productservice.domain.model.Money;
import com.congthanh.productservice.domain.model.OptionSelection;
import com.congthanh.productservice.domain.model.OptionValue;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.ProductImage;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import com.congthanh.productservice.grpc.ProductDeleted;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
class ProductPersistenceAdapter implements ProductRepository {

    static final String AGGREGATE = "product";

    private final ProductJpaRepository products;
    private final VariantOptionValueJpaRepository optionValues;
    private final VariantOptionCombinationJpaRepository combinations;
    private final VariantOptionJpaRepository options;
    private final ProductAttributeJpaRepository attributes;
    private final ProductSnapshotReader snapshots;
    private final OutboxWriter outbox;
    private final ProductSpecificationsStore specifications;
    private final EntityManager entityManager;

    ProductPersistenceAdapter(ProductJpaRepository products, VariantOptionValueJpaRepository optionValues,
                              VariantOptionCombinationJpaRepository combinations, VariantOptionJpaRepository options,
                              ProductAttributeJpaRepository attributes, ProductSnapshotReader snapshots, OutboxWriter outbox,
                              ProductSpecificationsStore specifications, EntityManager entityManager) {
        this.products = products;
        this.optionValues = optionValues;
        this.combinations = combinations;
        this.options = options;
        this.attributes = attributes;
        this.snapshots = snapshots;
        this.outbox = outbox;
        this.specifications = specifications;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(String id) {
        return products.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findVariants(String parentId) {
        return products.findVariants(parentId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Product product) {
        ProductEntity e = products.findById(product.id()).orElse(null);
        if (e != null && !Objects.equals(e.getVersion(), product.version())) {
            throw new ConflictException("product was changed by someone else (version " + e.getVersion() + ")");
        }
        boolean isNew = e == null;
        if (isNew) {
            e = new ProductEntity();
            e.setId(product.id());
            if (product.parentId() != null) {
                e.setParent(products.getReferenceById(product.parentId()));
                e.setVariantKey(product.variantKey());
            }
        }
        apply(product, e);
        try {
            products.saveAndFlush(e);
            saveOptionValues(product, e);
            if (isNew) {
                saveSelections(product, e);
            }
            products.flush();
            saveSpecifications(product, e);
        } catch (DataIntegrityViolationException ex) {
            // Hai yêu cầu song song cùng slug/SKU/GTIN/tổ hợp: ràng buộc DB là chốt chặn cuối.
            throw new ConflictException("product conflicts with an existing one (slug, sku, gtin, image or options)", ex);
        }
        for (ProductEvent event : product.pullEvents()) {
            publish(event, product.parentId());
        }
    }

    @Override
    public boolean existsSkuOnOther(String sku, String productId) {
        return products.existsSkuOnOther(sku, idOrZero(productId));
    }

    @Override
    public boolean existsSlugOnOther(String slug, String productId) {
        return products.existsSlugOnOther(slug, idOrZero(productId));
    }

    @Override
    public boolean existsGtinOnOther(String gtin, String productId) {
        return products.existsGtinOnOther(gtin, idOrZero(productId));
    }

    @Override
    public boolean existsLiveVariantKey(String parentId, String variantKey) {
        return products.existsLiveVariantKey(parentId, variantKey);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void republish(String productId) {
        products.findById(productId).ifPresent(p -> {
            if (p.getStatus() == ProductStatus.DELETED) {
                outbox.write(AGGREGATE, productId, ProductDeleted.newBuilder().setProductId(productId).build());
            } else {
                writeSnapshot(productId);
            }
        });
    }

    @Override
    public List<String> idsAfter(String after, int limit) {
        return products.findIdsAfter(after, Limit.of(limit));
    }

    // ------------------------------------------------------------------ event -> outbox

    private void publish(ProductEvent event, String parentId) {
        switch (event) {
            case ProductEvent.ProductChanged c -> writeSnapshot(c.productId());
            case ProductEvent.ProductDeleted d ->
                    outbox.write(AGGREGATE, d.productId(), ProductDeleted.newBuilder().setProductId(d.productId()).build());
        }
        if (parentId != null) {
            writeSnapshot(parentId);
        }
    }

    private void writeSnapshot(String productId) {
        snapshots.byId(productId).ifPresent(s -> outbox.write(AGGREGATE, productId, ProductEventMapper.toProto(s)));
    }

    // ------------------------------------------------------------------ entity -> domain

    private Product toDomain(ProductEntity e) {
        boolean root = e.getParent() == null;
        List<OptionValue> declared = root
                ? optionValues.findByParent(e.getId()).stream()
                        .map(v -> new OptionValue(v.getId(), v.getVariantOption().getId(), v.getValue(), v.getDisplayOrder()))
                        .toList()
                : List.of();
        List<OptionSelection> selections = root ? List.of()
                : combinations.findByVariant(e.getId()).stream()
                        .map(c -> new OptionSelection(c.getVariantOption().getId(), c.getVariantOptionValue().getId(), c.getDisplayOrder()))
                        .toList();
        return Product.rehydrate(new Product.State(
                e.getId(),
                root ? null : e.getParent().getId(),
                e.getName(),
                e.getSlug(),
                e.getSku(),
                e.getGtin(),
                e.getDescription(),
                e.getPrice() == null ? null : new Money(e.getPrice(), e.getCurrency()),
                e.getSupplier(),
                e.getBrand(),
                e.isFeatured(),
                e.getStatus(),
                e.getThumbnail(),
                e.isHasVariant(),
                e.getVariantKey(),
                e.getVersion(),
                e.getImage().stream()
                        .sorted(Comparator.comparingInt(ProductImageEntity::getDisplayOrder).thenComparing(ProductImageEntity::getId))
                        .map(i -> new ProductImage(i.getId(), i.getImagePath(), i.getDisplayOrder()))
                        .toList(),
                e.getCategory().stream()
                        .sorted(Comparator.comparingInt(ProductCategoryEntity::getDisplayOrder))
                        .map(c -> new CategoryAssignment(c.getCategoryId(), c.getDisplayOrder()))
                        .toList(),
                e.getAttribute().stream()
                        .map(a -> new AttributeValue(a.getProductAttribute().getId(), a.getValue(), a.getValueNumber()))
                        .toList(),
                declared,
                selections,
                e.getOwnerId(),
                specifications.load(e.getId())));
    }

    /**
     * Thông số tự do nằm ngoài entity JPA (cột jsonb, ghi bằng JdbcClient). Chỉ ghi khi đổi, kèm tăng version có kiểm
     * khoá lạc quan; sau đó nạp lại entity để version trong persistence context khớp DB cho các lần ghi sau.
     */
    private void saveSpecifications(Product product, ProductEntity e) {
        if (product.specifications().equals(specifications.load(product.id()))) {
            return;
        }
        specifications.save(product.id(), product.specifications(), e.getVersion());
        entityManager.refresh(e);
    }

    // ------------------------------------------------------------------ domain -> entity

    private void apply(Product p, ProductEntity e) {
        e.setName(p.name());
        e.setSlug(p.slug());
        e.setSku(p.sku());
        e.setGtin(p.gtin());
        e.setDescription(p.description());
        e.setPrice(p.price() == null ? null : p.price().amount());
        e.setCurrency(p.price() == null ? null : p.price().currency());
        e.setSupplier(p.supplier());
        e.setOwnerId(p.ownerId());
        e.setBrand(p.brandId());
        e.setFeatured(p.featured());
        e.setStatus(p.status());
        e.setThumbnail(p.thumbnailId());
        e.setHasVariant(p.hasVariant());
        applyCategories(p, e);
        applyImages(p, e);
        applyAttributes(p, e);
    }

    /**
     * Giữ dòng đã có (chỉ đổi thứ tự) thay vì xoá rồi thêm lại: Hibernate insert trước delete khi flush và sẽ đụng
     * unique (product_id, category_id).
     */
    private static void applyCategories(Product p, ProductEntity e) {
        Map<String, Integer> wanted = new HashMap<>();
        for (CategoryAssignment c : p.categories()) {
            wanted.put(c.categoryId(), c.displayOrder());
        }
        e.getCategory().removeIf(c -> !wanted.containsKey(c.getCategoryId()));
        Set<String> present = new HashSet<>();
        for (ProductCategoryEntity c : e.getCategory()) {
            c.setDisplayOrder(wanted.get(c.getCategoryId()));
            present.add(c.getCategoryId());
        }
        for (CategoryAssignment c : p.categories()) {
            if (!present.contains(c.categoryId())) {
                e.getCategory().add(ProductCategoryEntity.builder()
                        .id(Snowflake.nextId()).product(e).categoryId(c.categoryId()).displayOrder(c.displayOrder()).build());
            }
        }
    }

    private static void applyImages(Product p, ProductEntity e) {
        Map<Long, ProductImage> wanted = new HashMap<>();
        for (ProductImage i : p.images()) {
            wanted.put(i.mediaId(), i);
        }
        e.getImage().removeIf(i -> !wanted.containsKey(i.getId()));
        Set<Long> present = new HashSet<>();
        for (ProductImageEntity i : e.getImage()) {
            ProductImage d = wanted.get(i.getId());
            i.setImagePath(d.url());
            i.setDisplayOrder(d.displayOrder());
            present.add(i.getId());
        }
        for (ProductImage i : p.images()) {
            if (!present.contains(i.mediaId())) {
                e.getImage().add(ProductImageEntity.builder()
                        .id(i.mediaId()).product(e).imagePath(i.url()).displayOrder(i.displayOrder()).build());
            }
        }
        // Ảnh mặc định là ảnh đại diện; tắt cái cũ trước để không đụng unique "một ảnh mặc định" khi flush.
        e.getImage().forEach(i -> i.setDefault(false));
        e.getImage().stream().filter(i -> i.getId().equals(p.thumbnailId())).forEach(i -> i.setDefault(true));
    }

    /**
     * Đồng bộ giá trị thông số (nhiều dòng cho thuộc tính MULTI_SELECT, ADR 0033). Với mỗi thuộc tính: giữ dòng có giá
     * trị trùng (không phân biệt hoa thường), dùng lại dòng thừa cho giá trị mới (thuộc tính một giá trị đổi giá trị
     * thì cập nhật tại chỗ), rồi mới thêm/bỏ phần chênh. Trigger ở DB kiểm số giá trị lúc commit.
     */
    private void applyAttributes(Product p, ProductEntity e) {
        Map<Long, List<AttributeValue>> wanted = new LinkedHashMap<>();
        for (AttributeValue a : p.attributes()) {
            wanted.computeIfAbsent(a.attributeId(), k -> new ArrayList<>()).add(a);
        }
        Map<Long, List<ProductAttributeValueEntity>> existing = new HashMap<>();
        for (ProductAttributeValueEntity a : e.getAttribute()) {
            existing.computeIfAbsent(a.getProductAttribute().getId(), k -> new ArrayList<>()).add(a);
        }
        Set<ProductAttributeValueEntity> keep = new HashSet<>();
        for (Map.Entry<Long, List<AttributeValue>> w : wanted.entrySet()) {
            List<ProductAttributeValueEntity> rows = new ArrayList<>(existing.getOrDefault(w.getKey(), List.of()));
            List<AttributeValue> unmatched = new ArrayList<>();
            for (AttributeValue v : w.getValue()) {
                ProductAttributeValueEntity row = rows.stream()
                        .filter(r -> r.getValue().equalsIgnoreCase(v.value()))
                        .findFirst()
                        .orElse(null);
                if (row == null) {
                    unmatched.add(v);
                    continue;
                }
                rows.remove(row);
                setValue(row, v);
                keep.add(row);
            }
            for (AttributeValue v : unmatched) {
                if (!rows.isEmpty()) {
                    ProductAttributeValueEntity row = rows.removeFirst();
                    setValue(row, v);
                    keep.add(row);
                    continue;
                }
                ProductAttributeValueEntity row = ProductAttributeValueEntity.builder()
                        .id(Snowflake.nextId()).product(e).productAttribute(attributes.getReferenceById(v.attributeId()))
                        .value(v.value()).valueNumber(v.number()).build();
                e.getAttribute().add(row);
                keep.add(row);
            }
        }
        e.getAttribute().removeIf(a -> !keep.contains(a));
    }

    private static void setValue(ProductAttributeValueEntity row, AttributeValue v) {
        row.setValue(v.value());
        row.setValueNumber(v.number());
    }

    /** Giá trị trục chỉ được thêm (variant cũ còn tham chiếu), không sửa hay xoá ở đây. */
    private void saveOptionValues(Product p, ProductEntity e) {
        if (p.optionValues().isEmpty()) {
            return;
        }
        Set<Long> present = new HashSet<>();
        for (VariantOptionValueEntity v : optionValues.findByParent(p.id())) {
            present.add(v.getId());
        }
        for (OptionValue v : p.optionValues()) {
            if (!present.contains(v.id())) {
                optionValues.save(VariantOptionValueEntity.builder()
                        .id(v.id()).product(e).variantOption(options.getReferenceById(v.optionId()))
                        .value(v.value()).displayOrder(v.displayOrder()).build());
            }
        }
        optionValues.flush();
    }

    /** Tổ hợp option của variant cố định từ lúc tạo (đổi tổ hợp = tạo variant khác). */
    private void saveSelections(Product p, ProductEntity e) {
        for (OptionSelection s : p.selections()) {
            combinations.save(VariantOptionCombinationEntity.builder()
                    .id(Snowflake.nextId())
                    .variant(e)
                    .parentId(p.parentId())
                    .variantOption(options.getReferenceById(s.optionId()))
                    .variantOptionValue(optionValues.getReferenceById(s.optionValueId()))
                    .displayOrder(s.displayOrder())
                    .build());
        }
    }

    /** Id giả cho kiểm trùng khi sản phẩm chưa có id (tạo mới): không trùng id Snowflake nào. */
    private static String idOrZero(String productId) {
        return productId == null ? "0" : productId;
    }
}
