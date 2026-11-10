package com.congthanh.productservice.application.service;

import java.util.ArrayList;
import com.congthanh.productservice.application.event.ProductChanged;
import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.port.out.product.ProductDocumentRepository;
import com.congthanh.productservice.application.port.out.productCategory.ProductCategoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import com.congthanh.productservice.domain.model.*;
import com.congthanh.productservice.application.dto.response.PaginationInfo;
import com.congthanh.productservice.application.dto.response.ResponseWithPagination;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.congthanh.productservice.application.dto.request.CreateProductRequest;
import com.congthanh.productservice.domain.exception.NotFoundException;
import com.congthanh.productservice.application.mapper.ProductMapper;
import com.congthanh.productservice.application.dto.viewmodel.*;
import com.congthanh.productservice.application.port.out.product.ProductRepository;
import com.congthanh.productservice.application.port.out.productImage.ProductImageRepository;
import com.congthanh.productservice.application.port.out.variantOptionCombination.VariantOptionCombinationRepository;
import com.congthanh.productservice.application.port.in.*;
import com.congthanh.productservice.domain.support.Helper;
import com.congthanh.productservice.domain.support.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
//import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    private final ProductCategoryService productCategoryService;

    private final ProductAttributeService productAttributeService;

    private final ProductDocumentRepository productDocuments;

    private final ProductCategoryRepository productCategoryRepository;

    private final ApplicationEventPublisher events;

    private final CategoryCatalog categoryCatalog;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ProductImageRepository productImageRepository;

    private final VariantOptionCombinationRepository variantOptionCombinationRepository;

    @Override
//    @Cacheable("products")
    public ResponseWithPagination<ProductDTO> getAllProduct(Integer page, Integer limit) {
        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<Product> result = productRepository.findAll(pageable);

        if (result.hasContent()) {
            ResponseWithPagination<ProductDTO> response = new ResponseWithPagination<>();
            List<ProductDTO> list = new ArrayList<>();
            for (Product product : result.getContent()) {
                ProductDTO productDTO = ProductMapper.mapProductEntityToDTO(product);
                list.add(productDTO);
            }

            PaginationInfo paginationInfo = PaginationInfo.builder()
                    .page(page)
                    .limit(limit)
                    .totalPage(result.getTotalPages())
                    .totalElement(result.getTotalElements())
                    .build();
            response.setResponseList(list);
            response.setPaginationInfo(paginationInfo);
            return response;
        } else {
            throw new RuntimeException("List empty exception");
        }
    }

    @Override
//    @Cacheable(cacheNames = "products", key = "product-#id")
    public ProductDTO getProductById(String id) {
        ProductDocument data = readModelById(id);
        return ProductDTO.builder()
                .id(data.getId())
                .name(data.getName())
                .description(data.getDescription())
                .slug(data.getSlug())
//                .category(Category.build())
                .status(data.getStatus())
                .build();
    }

    @Override
    public ProductDTO getProductDetailById(String id) {
        Product product = productRepository.getProductDetailById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        ProductDTO response = ProductMapper.mapProductEntityToDTO(product);
        return response;
    }

    @Override
    public ProductDTO getProductDTOBySlug(String slug) {
        ProductDocument data = readModelBySlug(slug);
        return ProductDTO.builder()
                .id(data.getId())
                .name(data.getName())
                .slug(data.getSlug())
                .build();
    }

    @Override
    public ProductVm getProductVmBySlug(String slug) {
        ProductDocument data = readModelBySlug(slug);
        return ProductVm.builder()
                .id(data.getId())
                .name(data.getName())
                .slug(data.getSlug())
                .description(data.getDescription())
                .build();
    }

    @Override
    public ProductDetailVm getProductDetailBySlug(String slug) {
        Product product = productRepository.findProductBySlug(slug).orElseThrow(() -> new NotFoundException("Product not found"));

        List<String> categoryIds = product.getCategory().stream().map(ProductCategory::getCategoryId).toList();
        List<ProductCategoryVm> category = categoryCatalog.byIds(categoryIds).stream().map(item -> new ProductCategoryVm(item.id(), item.name(), item.slug())).toList();

        ProductImage thumbnail = productImageRepository.findById(product.getThumbnail()).orElseThrow(() -> new NotFoundException("Product image not found"));

//        List<String> images = productImageRepository.getProductImages(product.getId()).stream().map(ProductImage::getImagePath).toList();
        List<String> images = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(product.getImage())) {
            for (ProductImage image : product.getImage()) {
                images.add(image.getImagePath());
            }
        }

        Set<ProductAttributeValue> productAttributeValues = product.getAttribute();

        List<ProductAttributeVm> attributeVms = new ArrayList<>();
//        if (CollectionUtils.isNotEmpty(productAttributeValues)) {
//            List<ProductAttributeGroup> productAttributeGroups = productAttributeValues.stream()
//                    .map(productAttributeValue
//                            -> productAttributeValue.getProductAttribute().getProductAttributeGroup())
//                    .distinct()
//                    .toList();

        return new ProductDetailVm(
                product.getId(),
                product.getName(),
                product.getBrand() == null ? null : product.getBrand().toString(),
                category,
                attributeVms,
                product.getDescription(),
                product.isFeatured(),
                product.isHasVariant(),
                product.getPrice(),
                thumbnail.getImagePath(),
                images
        );
    }

    /**
     * Ghi trực tiếp vào Postgres (thay cho CommandGateway → Aggregate → EventHandler của Axon), rồi phát
     * ProductChanged: outbox ghi trong cùng transaction, read model Mongo cập nhật sau commit.
     * Ảnh: client upload thẳng lên media-service bằng presigned URL; ở đây chỉ nhận id/thứ tự.
     */
    @Override
    @Transactional
    public ProductDTO createProduct(CreateProductRequest request) {
        categoryCatalog.validate(request.getCategory());

        Product product = Product.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .slug(Helper.generateSlug(request.getName()))
                .status(ProductStatus.ACTIVE)
                .build();
        productRepository.save(product);

        List<ProductCategory> categories = new ArrayList<>();
        int order = 1;
        for (String categoryId : request.getCategory() == null ? List.<String>of() : request.getCategory()) {
            categories.add(ProductCategory.builder()
                    .id(snowflakeIdGenerator.nextId())
                    .categoryId(categoryId)
                    .product(product)
                    .displayOrder(order++)
                    .build());
        }
        productCategoryRepository.saveAll(categories);
        product.setCategory(categories);

        events.publishEvent(changed(product));
        return ProductMapper.mapProductEntityToDTO(product);
    }

    @Override
    @Transactional
    public Product updateProduct(ProductDTO productDTO) {
        Product product = productRepository.findById(productDTO.getId()).orElseThrow(() -> new RuntimeException("Product not found"));

        product.setName(productDTO.getName());
//     product.setCategory(productDTO.getCategory());
//     product.setSubcategory(productDTO.getSubcategory());
        product.setDescription(productDTO.getDescription());

        productRepository.save(product);
        events.publishEvent(changed(product));
        return product;
    }

    @Override
    @Transactional
    public boolean deleteProduct(String id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        if (product.getStatus() == ProductStatus.DELETED) {
            throw new RuntimeException("Product have deleted before");
        } else {
            boolean result = productRepository.deleteProduct(id);
            if (result) {
                events.publishEvent(ProductChanged.deleted(id));
            }
            return result;
        }
    }

    @Override
    public List<ProductVariantVm> getProductVariationsByParentId(String parentId) {
        Product parentProduct = productRepository.findById(parentId).orElseThrow(() -> new NotFoundException("Product not found"));
        if (Boolean.TRUE.equals(parentProduct.isHasVariant())) {
            List<Product> productVariations = parentProduct.getVariant().stream().filter(item -> item.getStatus().equals(ProductStatus.ACTIVE)).toList();

            return productVariations.stream().map(product -> {
                List<VariantOptionCombination> productOptionCombinations = variantOptionCombinationRepository.findAllByProduct(product);

                                Map<Long, String> options = productOptionCombinations.stream()
                        .collect(Collectors.toMap(
                                productOptionCombination -> productOptionCombination.getVariantOption().getId(),
                                VariantOptionCombination::getValue
                        ));

                                ProductImage thumbnail = productImageRepository.findById(product.getThumbnail()).orElseThrow(() -> new NotFoundException("Product image not found"));
                ProductImageVm thumbnailVm = new ProductImageVm(thumbnail.getId(), thumbnail.getImagePath());
//
                return new ProductVariantVm(
                        product.getId(),
                        product.getName(),
                        product.getSlug(),
                        product.getSku(),
                        product.getGtin(),
                        product.getPrice(),
                        thumbnailVm,
                        product.getImage().stream().map(productImage -> new ProductImageVm(productImage.getId(), productImage.getImagePath())).toList(),
                        options
                );
            }).toList();
        }
        return Collections.emptyList();
    }

    @Override
    public ResponseWithPagination<ProductDTO> getProductByCategoryId(String categoryId, int page, int limit) {
        Pageable pageable = PageRequest.of(page, limit);
        Page<Product> result = productRepository.findByCategory(categoryId, pageable);
        ResponseWithPagination<ProductDTO> response = new ResponseWithPagination<>();
        if (result.hasContent()) {
            List<ProductDTO> list = new ArrayList<>();
            for (Product product : result.getContent()) {
                ProductDTO productDTO = ProductMapper.mapProductEntityToDTO(product);
                list.add(productDTO);
            }

            PaginationInfo paginationInfo = PaginationInfo.builder()
                    .page(page)
                    .limit(limit)
                    .totalPage(result.getTotalPages())
                    .totalElement(result.getTotalElements())
                    .build();
            response.setResponseList(list);
            response.setPaginationInfo(paginationInfo);
        } else {
            throw new RuntimeException("List empty exception");
        }
        return response;
    }

    @Override
    public ResponseWithPagination<ProductDTO> getProductByCategorySlug(String slug, int page, int limit) {
        CategoryCatalog.CategoryRef category = categoryCatalog.bySlug(slug);
        Pageable pageable = PageRequest.of(page, limit);
        Page<Product> result = productRepository.findByCategory(category.id(), pageable);
        ResponseWithPagination<ProductDTO> response = new ResponseWithPagination<>();
        if (result.hasContent()) {
            List<ProductDTO> list = new ArrayList<>();
            for (Product product : result.getContent()) {
                ProductDTO productDTO = ProductMapper.mapProductEntityToDTO(product);
                list.add(productDTO);
            }

            PaginationInfo paginationInfo = PaginationInfo.builder()
                    .page(page)
                    .limit(limit)
                    .totalPage(result.getTotalPages())
                    .totalElement(result.getTotalElements())
                    .build();
            response.setResponseList(list);
            response.setPaginationInfo(paginationInfo);
        } else {
            throw new RuntimeException("List empty exception");
        }
        return response;
    }

    @Override
    public List<ProductDTO> searchProduct(String keyword) {
        List<Product> data = productRepository.searchProduct(keyword);
        
        if (!data.isEmpty()) {
            List<ProductDTO> result = new ArrayList<>();
            for (Product product : data) {
                ProductDTO productDTO = ProductMapper.mapProductEntityToDTO(product);
                result.add(productDTO);
            }
            return result;
        } else {
            return null;
        }
    }

    @Override
    public Long getSoldByProduct(String productId) {
        Long result = productRepository.countTotalSoldProduct(productId);
        return result != null ? result : 0;
    }

    @Override
    public List<?> getListFeaturedProducts(int page, int limit) {
        return List.of();
    }

    @Override
    public ResponseWithPagination<ProductDTO> getProductWithFilter(int page, int limit, String keyword, String category, String brand, int rating, Double startPrice, Double endPrice) {
        return null;
    }


    /** Đọc từ read model Mongo; chưa có (projection trễ hoặc mới dựng) thì đọc thẳng Postgres. */
    private ProductDocument readModelById(String id) {
        return productDocuments.findById(id).orElseGet(() -> toDocument(productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"))));
    }

    private ProductDocument readModelBySlug(String slug) {
        return productDocuments.findBySlug(slug).orElseGet(() -> toDocument(productRepository.findProductBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Product not found"))));
    }

    private static ProductDocument toDocument(Product p) {
        ProductDocument d = new ProductDocument();
        d.setId(p.getId());
        d.setName(p.getName());
        d.setSlug(p.getSlug());
        d.setSku(p.getSku());
        d.setDescription(p.getDescription());
        d.setPrice(p.getPrice());
        d.setHasVariant(p.isHasVariant());
        d.setStatus(p.getStatus());
        return d;
    }

    static ProductChanged changed(Product p) {
        List<String> categoryIds = p.getCategory() == null ? List.of()
                : p.getCategory().stream().map(ProductCategory::getCategoryId).toList();
        return new ProductChanged(p.getId(), false, p.getSku(), p.getName(), p.getSlug(), categoryIds, p.getPrice(),
                p.getStatus() == null ? null : p.getStatus().name(), p.getDescription(), p.isHasVariant(), p.isFeatured());
    }
}
