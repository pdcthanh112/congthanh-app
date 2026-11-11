package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.port.out.variantOptionCombination.VariantOptionCombinationRepository;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.ProductCategory;
import com.congthanh.productservice.domain.model.ProductImage;
import com.congthanh.productservice.domain.model.VariantOptionCombination;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
class ProductDocumentAssembler {

    private final CategoryCatalog categories;
    private final VariantOptionCombinationRepository combinations;
    private final String currency;

    ProductDocumentAssembler(CategoryCatalog categories, VariantOptionCombinationRepository combinations,
                             @Value("${product.currency:USD}") String currency) {
        this.categories = categories;
        this.combinations = combinations;
        this.currency = currency;
    }

    ProductDocument assemble(Product p, long coreVersion) {
        ProductDocument d = new ProductDocument();
        d.setId(p.getId());
        d.setSlug(p.getSlug());
        d.setName(p.getName());
        d.setSku(p.getSku());
        d.setGtin(p.getGtin());
        d.setDescription(p.getDescription());
        d.setStatus(p.getStatus());
        d.setFeatured(p.isFeatured());
        d.setHasVariant(p.isHasVariant());
        d.setParentId(p.getParent() == null ? null : p.getParent().getId());
        d.setSupplier(p.getSupplier());
        d.setBrand(p.getBrand() == null ? null : new ProductDocument.Brand(p.getBrand(), null, null));
        d.setCategories(categoriesOf(p));
        List<ProductDocument.Image> images = images(p.getImage());
        d.setImages(images);
        d.setThumbnail(thumbnail(p.getThumbnail(), images));
        d.setAttributes(p.getAttribute() == null ? List.of() : p.getAttribute().stream()
                .filter(a -> a.getProductAttribute() != null)
                .map(a -> new ProductDocument.Attribute(a.getProductAttribute().getName(), a.getValue()))
                .sorted(Comparator.comparing(ProductDocument.Attribute::name, Comparator.nullsLast(String::compareTo)))
                .toList());
        d.setVariants(p.getVariant() == null ? List.of() : p.getVariant().stream()
                .filter(v -> v.getStatus() == ProductStatus.ACTIVE)
                .sorted(Comparator.comparing(Product::getId))
                .map(this::variant)
                .toList());
        d.setBasePrice(p.getPrice());
        d.setCurrency(currency);
        d.setCoreVersion(coreVersion);
        d.setUpdatedAt(Instant.now());
        return d;
    }

    private List<ProductDocument.Category> categoriesOf(Product p) {
        List<String> ids = p.getCategory() == null ? List.of()
                : p.getCategory().stream().sorted(Comparator.comparingInt(ProductCategory::getDisplayOrder))
                .map(ProductCategory::getCategoryId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        // catalog-service lỗi thì ném ra: consumer thử lại, không ghi document thiếu tên danh mục.
        var byId = categories.byIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(CategoryCatalog.CategoryRef::id, c -> c, (a, b) -> a));
        return ids.stream().map(id -> {
            var c = byId.get(id);
            return new ProductDocument.Category(id, c == null ? null : c.name(), c == null ? null : c.slug());
        }).toList();
    }

    private ProductDocument.Variant variant(Product v) {
        List<ProductDocument.Image> images = images(v.getImage());
        List<VariantOptionCombination> combos = combinations.findAllByProduct(v);
        List<ProductDocument.VariantOption> options = combos == null ? List.of() : combos.stream()
                .sorted(Comparator.comparingInt(VariantOptionCombination::getDisplayOrder))
                .map(c -> new ProductDocument.VariantOption(
                        c.getVariantOption() == null ? null : c.getVariantOption().getId(),
                        c.getVariantOption() == null ? null : c.getVariantOption().getName(),
                        c.getValue()))
                .toList();
        return new ProductDocument.Variant(v.getId(), v.getName(), v.getSlug(), v.getSku(), v.getGtin(), v.getPrice(),
                thumbnail(v.getThumbnail(), images), images, options);
    }

    private static List<ProductDocument.Image> images(Collection<ProductImage> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder)
                        .thenComparing(ProductImage::getId, Comparator.nullsLast(Long::compareTo)))
                .map(i -> new ProductDocument.Image(i.getId(), i.getImagePath(), i.getDisplayOrder()))
                .toList();
    }

    /** Ảnh đại diện = ảnh có id {@code thumbnailId}; không có thì ảnh đầu tiên. */
    private static String thumbnail(Long thumbnailId, List<ProductDocument.Image> images) {
        return images.stream().filter(i -> Objects.equals(i.id(), thumbnailId)).findFirst()
                .or(() -> images.stream().findFirst())
                .map(ProductDocument.Image::url)
                .orElse(null);
    }
}
