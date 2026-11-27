package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.port.out.ProductSnapshot;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Bản chụp sản phẩm -> document của read model trang chi tiết (ADR 0020, 0030). Chỉ variant đang bán được hiển thị. */
@Component
class ProductDocumentAssembler {

    private final CategoryCatalog categories;
    private final Clock clock;

    ProductDocumentAssembler(CategoryCatalog categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    ProductDocument assemble(ProductSnapshot p, long coreVersion) {
        ProductDocument d = new ProductDocument();
        d.setId(p.id());
        d.setSlug(p.slug());
        d.setName(p.name());
        d.setSku(p.sku());
        d.setGtin(p.gtin());
        d.setDescription(p.description());
        d.setSpecifications(p.specifications().groups());
        d.setStatus(p.status());
        d.setFeatured(p.featured());
        d.setHasVariant(p.hasVariant());
        d.setParentId(p.parentId());
        d.setSupplier(p.supplier());
        d.setBrand(p.brandId() == null ? null : new ProductDocument.Brand(p.brandId(), null, null));
        d.setCategories(categoriesOf(p.categoryIds()));
        List<ProductDocument.Image> images = images(p.images());
        d.setImages(images);
        d.setThumbnail(thumbnail(p.thumbnailId(), images));
        // Thứ tự hiển thị: nhóm (không nhóm xếp cuối), thứ tự thuộc tính, tên; sort ổn định giữ thứ tự giá trị MULTI_SELECT.
        d.setAttributes(p.attributes().stream()
                .sorted(Comparator.comparing(ProductSnapshot.Attribute::groupOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductSnapshot.Attribute::group, Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(ProductSnapshot.Attribute::displayOrder)
                        .thenComparing(ProductSnapshot.Attribute::name, Comparator.nullsLast(String::compareTo)))
                .map(a -> new ProductDocument.Attribute(a.code(), a.name(), a.value(), a.number(), a.unit(), a.group()))
                .toList());
        List<ProductSnapshot> active = p.variants().stream()
                .filter(v -> v.status() == ProductStatus.ACTIVE)
                .sorted(Comparator.comparing(ProductSnapshot::id))
                .toList();
        d.setVariants(active.stream().map(ProductDocumentAssembler::variant).toList());
        d.setOptionGroups(optionGroups(active));
        d.setBasePrice(p.price());
        d.setCurrency(p.currency());
        d.setCoreVersion(coreVersion);
        d.setUpdatedAt(clock.instant());
        return d;
    }

    private List<ProductDocument.Category> categoriesOf(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // catalog-service lỗi thì ném ra: consumer thử lại, không ghi document thiếu tên danh mục.
        Map<String, CategoryCatalog.CategoryRef> byId = categories.byIds(ids).stream()
                .collect(Collectors.toMap(CategoryCatalog.CategoryRef::id, c -> c, (a, b) -> a));
        return ids.stream().map(id -> {
            CategoryCatalog.CategoryRef c = byId.get(id);
            return new ProductDocument.Category(id, c == null ? null : c.name(), c == null ? null : c.slug());
        }).toList();
    }

    private static ProductDocument.Variant variant(ProductSnapshot v) {
        List<ProductDocument.Image> images = images(v.images());
        List<ProductDocument.VariantOption> options = v.options().stream()
                .map(o -> new ProductDocument.VariantOption(o.optionId(), o.optionName(), o.valueId(), o.value()))
                .toList();
        return new ProductDocument.Variant(v.id(), v.name(), v.slug(), v.sku(), v.gtin(), v.price(), v.currency(),
                thumbnail(v.thumbnailId(), images), images, options);
    }

    /** Trục theo thứ tự trong tổ hợp; giá trị chỉ gồm những giá trị có variant đang bán. */
    private static List<ProductDocument.OptionGroup> optionGroups(List<ProductSnapshot> activeVariants) {
        Map<Long, List<ProductSnapshot.Option>> byOption = activeVariants.stream()
                .flatMap(v -> v.options().stream())
                .collect(Collectors.groupingBy(ProductSnapshot.Option::optionId, LinkedHashMap::new, Collectors.toList()));
        return byOption.values().stream()
                .sorted(Comparator.comparingInt(os -> os.stream().mapToInt(ProductSnapshot.Option::displayOrder).min().orElse(0)))
                .map(os -> {
                    List<ProductDocument.OptionChoice> values = os.stream()
                            .collect(Collectors.toMap(ProductSnapshot.Option::valueId, o -> o, (a, b) -> a, LinkedHashMap::new))
                            .values().stream()
                            .sorted(Comparator.comparingInt(ProductSnapshot.Option::valueOrder)
                                    .thenComparingLong(ProductSnapshot.Option::valueId))
                            .map(o -> new ProductDocument.OptionChoice(o.valueId(), o.value(), o.valueOrder()))
                            .toList();
                    return new ProductDocument.OptionGroup(os.getFirst().optionId(), os.getFirst().optionName(), values);
                })
                .toList();
    }

    private static List<ProductDocument.Image> images(List<ProductSnapshot.Image> images) {
        return images.stream()
                .sorted(Comparator.comparingInt(ProductSnapshot.Image::displayOrder).thenComparingLong(ProductSnapshot.Image::id))
                .map(i -> new ProductDocument.Image(i.id(), i.url(), i.displayOrder()))
                .toList();
    }

    /** Ảnh đại diện = ảnh có id {@code thumbnailId}; không có thì ảnh đầu tiên. */
    private static String thumbnail(Long thumbnailId, List<ProductDocument.Image> images) {
        return images.stream()
                .filter(i -> Objects.equals(i.id(), thumbnailId))
                .findFirst()
                .or(() -> images.stream().findFirst())
                .map(ProductDocument.Image::url)
                .orElse(null);
    }
}
