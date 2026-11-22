package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.productservice.application.port.out.ProductSnapshot;
import com.congthanh.productservice.application.port.out.ProductSnapshots;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
class ProductSnapshotReader implements ProductSnapshots {

    private final ProductJpaRepository products;
    private final ProductAttributeValueJpaRepository attributeValues;
    private final VariantOptionCombinationJpaRepository combinations;

    ProductSnapshotReader(ProductJpaRepository products, ProductAttributeValueJpaRepository attributeValues,
                          VariantOptionCombinationJpaRepository combinations) {
        this.products = products;
        this.attributeValues = attributeValues;
        this.combinations = combinations;
    }

    @Override
    public long snapshotMicros() {
        return products.snapshotMicros();
    }

    @Override
    public Optional<ProductSnapshot> byId(String id) {
        return products.findById(id).map(this::snapshot);
    }

    @Override
    public Optional<ProductSnapshot> bySlug(String slug) {
        return products.findProductBySlug(slug).map(this::snapshot);
    }

    private ProductSnapshot snapshot(ProductEntity p) {
        List<ProductSnapshot> variants = List.of();
        if (p.getParent() == null && p.isHasVariant()) {
            Map<String, List<ProductSnapshot.Option>> optionsByVariant = combinations.findByParent(p.getId()).stream()
                    .collect(Collectors.groupingBy(c -> c.getVariant().getId(),
                            Collectors.mapping(ProductSnapshotReader::option, Collectors.toList())));
            variants = products.findVariants(p.getId()).stream()
                    .filter(v -> v.getStatus() != ProductStatus.DELETED)
                    .map(v -> build(v, optionsByVariant.getOrDefault(v.getId(), List.of()), List.of()))
                    .toList();
        }
        List<ProductSnapshot.Option> options = p.getParent() == null ? List.of()
                : combinations.findByVariant(p.getId()).stream().map(ProductSnapshotReader::option).toList();
        return build(p, options, variants);
    }

    private ProductSnapshot build(ProductEntity p, List<ProductSnapshot.Option> options, List<ProductSnapshot> variants) {
        List<ProductSnapshot.Image> images = p.getImage().stream()
                .sorted(Comparator.comparingInt(ProductImageEntity::getDisplayOrder).thenComparing(ProductImageEntity::getId))
                .map(i -> new ProductSnapshot.Image(i.getId(), i.getImagePath(), i.getDisplayOrder()))
                .toList();
        List<String> categoryIds = p.getCategory().stream()
                .sorted(Comparator.comparingInt(ProductCategoryEntity::getDisplayOrder))
                .map(ProductCategoryEntity::getCategoryId)
                .toList();
        List<ProductSnapshot.Attribute> attributes = attributeValues.findByProduct(p.getId()).stream()
                .map(v -> {
                    ProductAttributeEntity a = v.getProductAttribute();
                    return new ProductSnapshot.Attribute(a.getCode(), a.getName(), v.getValue(), v.getValueNumber(), a.getUnit(),
                            a.isFilterable(), a.isSearchable(), a.getDisplayOrder());
                })
                .toList();
        List<ProductSnapshot.Option> sorted = options.stream()
                .sorted(Comparator.comparingInt(ProductSnapshot.Option::displayOrder))
                .toList();
        return new ProductSnapshot(p.getId(), p.getParent() == null ? null : p.getParent().getId(), p.getName(), p.getSlug(),
                p.getSku(), p.getGtin(), p.getDescription(), p.getStatus(), p.isFeatured(), p.isHasVariant(), p.getSupplier(),
                p.getBrand(), p.getPrice(), p.getCurrency(), p.getThumbnail(), images, categoryIds, attributes, sorted, variants);
    }

    private static ProductSnapshot.Option option(VariantOptionCombinationEntity c) {
        return new ProductSnapshot.Option(c.getVariantOption().getId(), c.getVariantOption().getName(),
                c.getVariantOptionValue().getId(), c.getVariantOptionValue().getValue(),
                c.getVariantOptionValue().getDisplayOrder(), c.getDisplayOrder());
    }
}
