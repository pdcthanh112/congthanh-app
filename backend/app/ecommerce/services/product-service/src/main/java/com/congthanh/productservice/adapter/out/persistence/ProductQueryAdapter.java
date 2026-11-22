package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.ProductImageDTO;
import com.congthanh.productservice.application.dto.response.PaginationInfo;
import com.congthanh.productservice.application.dto.response.ResponseWithPagination;
import com.congthanh.productservice.application.dto.viewmodel.ProductImageVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionCombinationGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionValueGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVariantVm;
import com.congthanh.productservice.application.port.out.ProductQueries;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
class ProductQueryAdapter implements ProductQueries {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductJpaRepository products;
    private final ProductAttributeValueJpaRepository attributeValues;
    private final ProductImageJpaRepository images;
    private final VariantOptionValueJpaRepository optionValues;
    private final VariantOptionCombinationJpaRepository combinations;

    ProductQueryAdapter(ProductJpaRepository products, ProductAttributeValueJpaRepository attributeValues,
                        ProductImageJpaRepository images, VariantOptionValueJpaRepository optionValues,
                        VariantOptionCombinationJpaRepository combinations) {
        this.products = products;
        this.attributeValues = attributeValues;
        this.images = images;
        this.optionValues = optionValues;
        this.combinations = combinations;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseWithPagination<ProductDTO> active(int page, int limit) {
        PageRequest pageable = pageable(page, limit);
        return toPage(products.findByParentIsNullAndStatus(ProductStatus.ACTIVE, pageable), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseWithPagination<ProductDTO> activeInCategory(String categoryId, int page, int limit) {
        PageRequest pageable = pageable(page, limit);
        return toPage(products.findByCategory(categoryId, ProductStatus.ACTIVE, pageable), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseWithPagination<ProductDTO> featured(int page, int limit) {
        PageRequest pageable = pageable(page, limit);
        return toPage(products.findFeatured(ProductStatus.ACTIVE, pageable), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDTO> detail(String id) {
        return products.findDetailById(id).map(ProductDtoMapper::product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> lookup(String keyword, int limit) {
        String k = keyword.toLowerCase(Locale.ROOT);
        String pattern = "%" + k.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return products.lookup(pattern, k, limit).stream().map(ProductDtoMapper::product).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantVm> activeVariants(String parentId) {
        Map<String, List<VariantOptionCombinationEntity>> combos = combinations.findByParent(parentId).stream()
                .collect(Collectors.groupingBy(c -> c.getVariant().getId()));
        return products.findVariants(parentId).stream()
                .filter(v -> v.getStatus() == ProductStatus.ACTIVE)
                .map(v -> variant(v, combos.getOrDefault(v.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductOptionValueGetVm> optionValues(String parentId) {
        return optionValues.findByParent(parentId).stream()
                .map(v -> new ProductOptionValueGetVm(v.getId(), v.getVariantOption().getId(), v.getValue(),
                        v.getVariantOption().getName(), v.getDisplayOrder()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductOptionCombinationGetVm> usedOptionValues(String parentId) {
        Map<Long, ProductOptionCombinationGetVm> byValue = new LinkedHashMap<>();
        for (VariantOptionCombinationEntity c : combinations.findByParent(parentId)) {
            if (c.getVariant().getStatus() == ProductStatus.DELETED) {
                continue;
            }
            byValue.putIfAbsent(c.getVariantOptionValue().getId(), new ProductOptionCombinationGetVm(
                    c.getVariantOptionValue().getId(), c.getVariantOption().getId(), c.getVariantOptionValue().getValue(),
                    c.getVariantOption().getName()));
        }
        return List.copyOf(byValue.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductAttributeValueDTO> attributeValues(String productId) {
        return attributeValues.findByProduct(productId).stream().map(ProductDtoMapper::attributeValue).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageDTO> images(String productId) {
        return images.findByProductOrdered(productId).stream().map(ProductDtoMapper::image).toList();
    }

    private static ProductVariantVm variant(ProductEntity v, List<VariantOptionCombinationEntity> combos) {
        Map<Long, String> options = combos.stream()
                .sorted(Comparator.comparingInt(VariantOptionCombinationEntity::getDisplayOrder))
                .collect(Collectors.toMap(c -> c.getVariantOption().getId(), c -> c.getVariantOptionValue().getValue(),
                        (a, b) -> a, LinkedHashMap::new));
        List<ProductImageVm> vms = v.getImage().stream()
                .sorted(Comparator.comparingInt(ProductImageEntity::getDisplayOrder))
                .map(i -> new ProductImageVm(i.getId(), i.getImagePath()))
                .toList();
        ProductImageVm thumbnail = vms.stream()
                .filter(i -> Objects.equals(i.id(), v.getThumbnail()))
                .findFirst()
                .orElse(vms.isEmpty() ? null : vms.getFirst());
        return new ProductVariantVm(v.getId(), v.getName(), v.getSlug(), v.getSku(), v.getGtin(), v.getPrice(),
                v.getCurrency(), thumbnail, vms, options);
    }

    private static PageRequest pageable(int page, int limit) {
        return PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(limit, 1), MAX_PAGE_SIZE), Sort.by("id").descending());
    }

    private static ResponseWithPagination<ProductDTO> toPage(Page<ProductEntity> result, PageRequest pageable) {
        PaginationInfo info = PaginationInfo.builder()
                .page(pageable.getPageNumber() + 1)
                .limit(pageable.getPageSize())
                .totalPage(result.getTotalPages())
                .totalElement(result.getTotalElements())
                .build();
        return new ResponseWithPagination<>(info, result.getContent().stream().map(ProductDtoMapper::product).toList());
    }
}
