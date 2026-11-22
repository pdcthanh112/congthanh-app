package com.congthanh.productservice.adapter.in.web;

import com.congthanh.commonservice.model.response.Response;
import com.congthanh.productservice.application.dto.ProductCategoryDTO;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.request.CreateProductRequest;
import com.congthanh.productservice.application.dto.request.UpdateProductRequest;
import com.congthanh.productservice.application.dto.response.ResponseWithPagination;
import com.congthanh.productservice.application.dto.response.VariantValueResponse;
import com.congthanh.productservice.application.dto.viewmodel.*;
import com.congthanh.productservice.application.port.in.ProductCategoryService;
import com.congthanh.productservice.application.port.in.ProductService;
import com.congthanh.productservice.application.port.in.VariantOptionService;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.congthanh.productservice.adapter.in.web.ProductAttributeController.ok;

@RestController
@RequestMapping("/api/ecommerce/products")
@Tag(name = "Product API", description = "Product API in CongThanhApp - Ecommerce")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final VariantOptionService variantOptionService;
    private final ProductCategoryService productCategoryService;

    record ChangeStatusRequest(@NotNull ProductStatus status) {
    }

    @GetMapping("")
    public ResponseEntity<Response<ResponseWithPagination<ProductDTO>>> getAllProduct(
            @RequestParam(name = "page", defaultValue = "1") int page, @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(ok(productService.getAllProduct(page, limit), "Get all successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response<ProductDTO>> getProductById(@PathVariable("id") String id) {
        return ResponseEntity.ok(ok(productService.getProductById(id), "Get by id successfully"));
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "Get product detail by id", description = "Product with categories, images and attributes (write model)")
    public ResponseEntity<Response<ProductDTO>> getProductDetailById(@PathVariable("id") String id) {
        return ResponseEntity.ok(ok(productService.getProductDetailById(id), "Get detail by id successfully"));
    }

    @GetMapping("/back-office/slug/{slug}")
    public ResponseEntity<Response<ProductDTO>> getProductDTOBySlug(@PathVariable("slug") String slug) {
        return ResponseEntity.ok(ok(productService.getProductDTOBySlug(slug), "Get by slug successfully"));
    }

    @GetMapping("/store-front/slug/{slug}")
    public ResponseEntity<Response<ProductVm>> getProductVmBySlug(@PathVariable("slug") String slug) {
        return ResponseEntity.ok(ok(productService.getProductVmBySlug(slug), "Get by slug successfully"));
    }

    @GetMapping("/store-front/slug/{slug}/detail")
    public ResponseEntity<Response<ProductDetailVm>> getProductDetailVmBySlug(@PathVariable("slug") String slug) {
        return ResponseEntity.ok(ok(productService.getProductDetailBySlug(slug), "Get by slug successfully"));
    }

    @PostMapping("")
    public ResponseEntity<Response<ProductDTO>> createProduct(@RequestBody @Valid CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(productService.createProduct(request), "Created successfully"));
    }

    @PutMapping("")
    public ResponseEntity<Response<ProductDTO>> updateProduct(@RequestBody @Valid UpdateProductRequest request) {
        return ResponseEntity.ok(ok(productService.updateProduct(request), "Updated successfully"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Response<ProductDTO>> changeStatus(@PathVariable("id") String id,
                                                             @RequestBody @Valid ChangeStatusRequest request) {
        return ResponseEntity.ok(ok(productService.changeStatus(id, request.status()), "Updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/categories/{categoryId}")
    public ResponseEntity<Response<List<ProductCategoryDTO>>> addCategory(@PathVariable("id") String id,
                                                                          @PathVariable("categoryId") String categoryId) {
        return ResponseEntity.ok(ok(productCategoryService.addCategoryToProduct(id, categoryId), "Updated successfully"));
    }

    @DeleteMapping("/{id}/categories/{categoryId}")
    public ResponseEntity<Response<List<ProductCategoryDTO>>> removeCategory(@PathVariable("id") String id,
                                                                             @PathVariable("categoryId") String categoryId) {
        return ResponseEntity.ok(ok(productCategoryService.removeCategoryFromProduct(id, categoryId), "Updated successfully"));
    }

    @GetMapping("/store-front/categories/{categoryId}/products")
    public ResponseEntity<Response<ResponseWithPagination<ProductDTO>>> getProductByCategory(
            @PathVariable("categoryId") String categoryId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ok(productService.getProductByCategoryId(categoryId, page, limit), "Get successfully"));
    }

    @GetMapping("/store-front/categories/slug/{slug}/products")
    public ResponseEntity<Response<ResponseWithPagination<ProductDTO>>> getProductByCategorySlug(
            @PathVariable("slug") String slug, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ok(productService.getProductByCategorySlug(slug, page, limit), "Get successfully"));
    }

    @GetMapping("/storefront/products/featured")
    public ResponseEntity<Response<ResponseWithPagination<ProductDTO>>> getFeaturedProducts(
            @RequestParam(value = "page", defaultValue = "1") int page, @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(ok(productService.getFeaturedProducts(page, limit), "Get successfully"));
    }

    /** Tra cứu theo tên/SKU (back-office). Storefront tìm kiếm, lọc, facet qua search-service. */
    @GetMapping("/search")
    public ResponseEntity<Response<List<ProductDTO>>> searchProduct(@RequestParam String search) {
        return ResponseEntity.ok(ok(productService.searchProduct(search), "Get successfully"));
    }

    @GetMapping({"/storefront/product-variations/{parentId}", "/backoffice/product-variations/{parentId}"})
    public ResponseEntity<List<ProductVariantVm>> getProductVariationsByParentId(@PathVariable("parentId") String parentId) {
        return ResponseEntity.ok(productService.getProductVariationsByParentId(parentId));
    }

    /** Các trục variant và giá trị đang bán (đọc từ read model). */
    @GetMapping("/{id}/variants/attributes/detail")
    public ResponseEntity<Response<List<VariantValueResponse>>> getVariantAttributeValueDetail(@PathVariable("id") String productId) {
        return ResponseEntity.ok(ok(productService.getVariantOptionGroups(productId), "Get successfully"));
    }

    @GetMapping("/storefront/product-option-combinations/{productId}/values")
    public ResponseEntity<Response<List<ProductOptionCombinationGetVm>>> listProductOptionCombinationOfProduct(
            @PathVariable("productId") String productId) {
        return ResponseEntity.ok(ok(variantOptionService.getVariantOptionCombinationByProduct(productId), "Get successfully"));
    }

    @GetMapping("/storefront/product-option-values/{productId}")
    public ResponseEntity<Response<List<ProductOptionValueGetVm>>> listProductOptionValueOfProduct(@PathVariable("productId") String productId) {
        return ResponseEntity.ok(ok(variantOptionService.getVariantOptionValueByProduct(productId), "Get successfully"));
    }
}
