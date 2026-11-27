package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.request.ChangeSpecificationsRequest;
import com.congthanh.productservice.application.dto.request.CreateProductRequest;
import com.congthanh.productservice.application.dto.request.CreateVariantRequest;
import com.congthanh.productservice.application.dto.request.UpdateProductRequest;
import com.congthanh.productservice.application.dto.response.OptionValue;
import com.congthanh.productservice.application.dto.response.ResponseWithPagination;
import com.congthanh.productservice.application.dto.response.VariantValueResponse;
import com.congthanh.productservice.application.dto.viewmodel.ProductAttributeVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductCategoryVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductDetailVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductImageVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVariantVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVm;
import com.congthanh.productservice.application.port.in.ProductReadModelUseCase;
import com.congthanh.productservice.application.port.in.ProductService;
import com.congthanh.productservice.application.port.in.VariantOptionService;
import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.port.out.ProductAttributeRepository;
import com.congthanh.productservice.application.port.out.ProductQueries;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.congthanh.productservice.domain.model.Money;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.Specifications;
import com.congthanh.productservice.domain.model.ProductAttribute;
import com.congthanh.productservice.domain.model.ProductImage;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import com.congthanh.productservice.domain.support.Slugs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final int LOOKUP_LIMIT = 50;

    private final ProductRepository products;
    private final ProductQueries queries;
    private final ProductAttributeRepository attributes;
    private final ProductReadModelUseCase readModel;
    private final CategoryCatalog categoryCatalog;
    private final VariantOptionService variantOptionService;

    @Value("${product.read-model.price-list:default}")
    private String priceList;


    @Override
    public ResponseWithPagination<ProductDTO> getAllProduct(Integer page, Integer limit) {
        return queries.active(page == null ? 1 : page, limit == null ? 10 : limit);
    }

    @Override
    @Cacheable(cacheNames = "products", key = "'product-' + #id")
    public ProductDTO getProductById(String id) {
        ProductDocument data = readModel.getById(id);
        return ProductDTO.builder()
                .id(data.getId())
                .name(data.getName())
                .description(data.getDescription())
                .slug(data.getSlug())
                .status(data.getStatus())
                .build();
    }

    @Override
    public ProductDTO getProductDetailById(String id) {
        return queries.detail(id).orElseThrow(() -> new NotFoundException("product not found"));
    }

    @Override
    public ProductDTO getProductDTOBySlug(String slug) {
        ProductDocument data = readModel.getBySlug(slug);
        return ProductDTO.builder()
                .id(data.getId())
                .name(data.getName())
                .slug(data.getSlug())
                .build();
    }

    @Override
    public ProductVm getProductVmBySlug(String slug) {
        ProductDocument data = readModel.getBySlug(slug);
        return ProductVm.builder()
                .id(data.getId())
                .name(data.getName())
                .slug(data.getSlug())
                .description(data.getDescription())
                .thumbnail(data.getThumbnail())
                .status(data.getStatus())
                .build();
    }

    @Override
    public ProductDetailVm getProductDetailBySlug(String slug) {
        ProductDocument d = readModel.getBySlug(slug);
        var brand = d.getBrand();
        return new ProductDetailVm(
                d.getId(),
                d.getName(),
                brand == null ? null : (brand.name() != null ? brand.name() : String.valueOf(brand.id())),
                d.getCategories().stream().map(c -> new ProductCategoryVm(c.id(), c.name(), c.slug())).toList(),
                d.getAttributes().stream().map(a -> new ProductAttributeVm(a.name(), a.value())).toList(),
                d.getDescription(),
                d.isFeatured(),
                d.isHasVariant(),
                d.effectivePrice(priceList),
                d.effectiveCurrency(priceList),
                d.getThumbnail(),
                d.getImages().stream().map(ProductDocument.Image::url).toList(),
                d.getRating() == null ? null : d.getRating().average(),
                d.getRating() == null ? 0L : d.getRating().count(),
                d.getInStock(),
                d.getVariants().stream().map(ProductServiceImpl::variantVm).toList()
        );
    }

    private static ProductVariantVm variantVm(ProductDocument.Variant v) {
        Map<Long, String> options = new LinkedHashMap<>();
        for (ProductDocument.VariantOption o : v.options()) {
            if (o.optionId() != null) {
                options.put(o.optionId(), o.value());
            }
        }
        List<ProductImageVm> images = v.images().stream().map(i -> new ProductImageVm(i.id(), i.url())).toList();
        ProductImageVm thumbnail = images.stream()
                .filter(i -> Objects.equals(i.imagePath(), v.thumbnail()))
                .findFirst()
                .orElse(null);
        return new ProductVariantVm(v.id(), v.name(), v.slug(), v.sku(), v.gtin(), v.price(), v.currency(), thumbnail, images, options);
    }

    @Override
    public List<ProductVariantVm> getProductVariationsByParentId(String parentId) {
        Product parent = products.findById(parentId).orElseThrow(() -> new NotFoundException("product not found"));
        return parent.hasVariant() ? queries.activeVariants(parentId) : List.of();
    }

    @Override
    public List<VariantValueResponse> getVariantOptionGroups(String productId) {
        ProductDocument d = readModel.getById(productId);
        return d.getOptionGroups().stream()
                .map(g -> new VariantValueResponse(g.name(), g.values().stream()
                        .map(v -> new OptionValue(v.value(), null, v.order()))
                        .toList()))
                .toList();
    }

    @Override
    public ResponseWithPagination<ProductDTO> getProductByCategoryId(String categoryId, int page, int limit) {
        return queries.activeInCategory(categoryId, page, limit);
    }

    @Override
    public ResponseWithPagination<ProductDTO> getProductByCategorySlug(String slug, int page, int limit) {
        return queries.activeInCategory(categoryCatalog.bySlug(slug).id(), page, limit);
    }

    @Override
    public ResponseWithPagination<ProductDTO> getFeaturedProducts(int page, int limit) {
        return queries.featured(page, limit);
    }

    @Override
    public List<Product> findByIds(Collection<String> ids) {
        return ids.stream().map(products::findById).flatMap(Optional::stream).toList();
    }

    @Override
    public List<ProductDTO> searchProduct(String keyword) {
        String k = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        if (k.length() < 2) {
            return List.of();
        }
        return queries.lookup(k, LOOKUP_LIMIT);
    }

    @Override
    @Transactional
    public ProductDTO createProduct(CreateProductRequest request, String ownerId) {
        requireCategories(request.categoryIds());
        String id = Snowflake.nextString();
        String slug = request.slug() == null || request.slug().isBlank()
                ? freeSlug(Slugs.from(request.name()))
                : requireSlugFree(request.slug().strip(), null);
        requireSkuFree(request.sku(), null);
        requireGtinFree(request.gtin(), null);
        List<ProductImage> images = request.images().stream()
                .map(i -> new ProductImage(i.mediaId(), i.url(), i.displayOrder()))
                .toList();
        Product product = Product.create(new Product.Draft(id, request.name(), slug, request.sku(), request.gtin(),
                Money.ofNullable(request.price(), request.currency()), request.description(), request.supplier(),
                request.brandId(), request.featured(), request.categoryIds(), images, request.thumbnailId(), ownerId));
        Set<Long> seen = new HashSet<>();
        for (CreateProductRequest.AttributeRequest a : request.attributes()) {
            if (!seen.add(a.attributeId())) {
                throw new BadRequestException("attribute " + a.attributeId() + " is listed twice");
            }
            product.addAttribute(requireAttribute(a.attributeId()).valuesOf(a.rawValues()));
        }
        products.save(product);

        for (CreateVariantRequest v : request.variants()) {
            variantOptionService.createVariant(id, v);
        }
        ProductStatus status = request.status() == null ? ProductStatus.ACTIVE : status(request.status());
        if (status == ProductStatus.ACTIVE) {
            Product saved = requireProduct(id);
            saved.activate(hasSellableVariant(saved));
            products.save(saved);
        }
        return getProductDetailById(id);
    }

    @Override
    @Transactional
    public ProductDTO updateProduct(UpdateProductRequest request) {
        Product product = requireProduct(request.id());
        product.requireVersion(request.version());
        if (request.name() != null) {
            product.rename(request.name());
        }
        if (request.slug() != null) {
            product.changeSlug(requireSlugFree(request.slug().strip(), product.id()));
        }
        if (request.sku() != null) {
            requireSkuFree(request.sku(), product.id());
            product.changeSku(request.sku());
        }
        if (request.gtin() != null) {
            requireGtinFree(request.gtin(), product.id());
            product.changeGtin(request.gtin());
        }
        if (request.price() != null || request.currency() != null) {
            var amount = request.price() != null ? request.price() : product.price() == null ? null : product.price().amount();
            String currency = request.currency() != null ? request.currency()
                    : product.price() == null ? null : product.price().currency();
            product.reprice(Money.ofNullable(amount, currency));
        }
        if (request.description() != null) {
            product.describe(request.description());
        }
        if (request.supplier() != null) {
            product.changeSupplier(request.supplier());
        }
        if (request.brandId() != null) {
            product.changeBrand(request.brandId());
        }
        if (request.featured() != null) {
            product.feature(request.featured());
        }
        if (request.thumbnailId() != null) {
            product.setThumbnail(request.thumbnailId());
        }
        if (request.categoryIds() != null) {
            requireCategories(request.categoryIds());
            product.replaceCategories(request.categoryIds());
        }
        products.save(product);
        return getProductDetailById(product.id());
    }

    @Override
    @Transactional
    public ProductDTO changeSpecifications(String id, ChangeSpecificationsRequest request) {
        Product product = requireProduct(id);
        product.requireVersion(request.version());
        List<Specifications.Group> groups = request.groups().stream()
                .map(g -> new Specifications.Group(g.name(), g.items().stream()
                        .map(i -> new Specifications.Item(i.name(), i.value()))
                        .toList()))
                .toList();
        product.changeSpecifications(Specifications.of(groups));
        products.save(product);
        return getProductDetailById(id);
    }

    @Override
    @Transactional
    public ProductDTO changeStatus(String id, ProductStatus status) {
        Product product = requireProduct(id);
        switch (status) {
            case ACTIVE -> product.activate(hasSellableVariant(product));
            case INACTIVE -> product.deactivate();
            case DELETED -> throw new BadRequestException("status must be ACTIVE or INACTIVE");
        }
        products.save(product);
        return getProductDetailById(id);
    }

    @Override
    @Transactional
    public void deleteProduct(String id) {
        Product product = requireProduct(id);
        if (!product.isVariant()) {
            for (Product v : products.findVariants(id)) {
                if (v.status() != ProductStatus.DELETED) {
                    v.delete();
                    products.save(v);
                }
            }
        }
        product.delete();
        products.save(product);
    }

    private Product requireProduct(String id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("product not found: " + id));
    }

    private ProductAttribute requireAttribute(long id) {
        return attributes.findById(id).orElseThrow(() -> new NotFoundException("attribute not found: " + id));
    }

    private boolean hasSellableVariant(Product p) {
        return p.hasVariant() && products.findVariants(p.id()).stream().anyMatch(Product::isSellable);
    }

    private void requireCategories(List<String> categoryIds) {
        try {
            categoryCatalog.validate(categoryIds == null ? List.of() : categoryIds);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private String requireSlugFree(String slug, String productId) {
        if (products.existsSlugOnOther(slug, productId)) {
            throw new ConflictException("slug already exists: " + slug);
        }
        return slug;
    }

    private String freeSlug(String base) {
        String root = base.isEmpty() ? "product" : base;
        String slug = root;
        while (products.existsSlugOnOther(slug, null)) {
            slug = root + "-" + Long.toString(Snowflake.nextId() % 1_000_000L, 36);
        }
        return slug;
    }

    private void requireSkuFree(String sku, String productId) {
        if (sku != null && !sku.isBlank() && products.existsSkuOnOther(sku.strip(), productId)) {
            throw new ConflictException("sku already exists: " + sku.strip());
        }
    }

    private void requireGtinFree(String gtin, String productId) {
        if (gtin != null && !gtin.isBlank() && products.existsGtinOnOther(gtin.strip(), productId)) {
            throw new ConflictException("gtin already exists: " + gtin.strip());
        }
    }

    private static ProductStatus status(String raw) {
        try {
            ProductStatus st = ProductStatus.valueOf(raw.strip().toUpperCase(Locale.ROOT));
            if (st == ProductStatus.DELETED) {
                throw new BadRequestException("status must be ACTIVE or INACTIVE");
            }
            return st;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be ACTIVE or INACTIVE");
        }
    }
}
