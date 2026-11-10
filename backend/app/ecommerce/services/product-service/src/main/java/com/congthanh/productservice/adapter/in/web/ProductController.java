package com.congthanh.productservice.adapter.in.web;

import com.congthanh.productservice.application.dto.constant.ResponseStatus;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.request.CreateProductRequest;
import com.congthanh.productservice.application.dto.response.OptionValue;
import com.congthanh.productservice.application.dto.response.Response;
import com.congthanh.productservice.application.dto.response.ResponseWithPagination;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.application.dto.response.VariantValueResponse;
import com.congthanh.productservice.application.dto.viewmodel.*;
import com.congthanh.productservice.application.port.out.product.ProductRepository;
import com.congthanh.productservice.application.port.in.ProductService;
import com.congthanh.productservice.application.port.in.VariantOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ecommerce/products")
@Tag(name = "Product API", description = "Product API in CongThanhApp - Ecommerce")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductRepository productRepository;

    private final ProductService productService;

    private final VariantOptionService variantOptionService;

    @GetMapping("")
    @PermitAll
    public ResponseEntity<Response<ResponseWithPagination<ProductDTO>>> getAllProduct(@RequestParam(name = "page", required = false, defaultValue = "1") int page, @RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {
        ResponseWithPagination<ProductDTO> data = productService.getAllProduct(page, limit);
        Response<ResponseWithPagination<ProductDTO>> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get all successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/{id}")
    @PermitAll
    public ResponseEntity<Response<ProductDTO>> getProductById(@PathVariable("id") String id) {
        ProductDTO data = productService.getProductById(id);
        Response<ProductDTO> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get by id successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/{id}/detail")
    @PermitAll
    @Operation(
            summary = "Get product detail by Id",
            description = "Get product detail by id with full attribute",
            tags = {"product", "get", "detail"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = {@Content(schema = @Schema())}),
            @ApiResponse(responseCode = "404", content = {@Content(schema = @Schema())}),
            @ApiResponse(responseCode = "500", content = {@Content(schema = @Schema())})})
    public ResponseEntity<Response<ProductDTO>> getProductDetailById(@PathVariable("id") String id) {
        ProductDTO data = productService.getProductDetailById(id);
        Response<ProductDTO> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get detail by id successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/back-office/slug/{slug}")
    @PermitAll
    public ResponseEntity<Response<ProductDTO>> getProductDTOBySlug(@PathVariable("slug") String slug) {
        ProductDTO data = productService.getProductDTOBySlug(slug);
        Response<ProductDTO> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get by slug successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/store-front/slug/{slug}")
    @PermitAll
    public ResponseEntity<Response<ProductVm>> getProductVmBySlug(@PathVariable("slug") String slug) {
        ProductVm data = productService.getProductVmBySlug(slug);
        Response<ProductVm> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get by slug successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/store-front/slug/{slug}/detail")
    @PermitAll
    public ResponseEntity<Response<ProductDetailVm>> getProductDetailVmBySlug(@PathVariable("slug") String slug) {
        System.out.println("TTTTTTTTTTTTTTTTTTTTTTTTTTTT");
        ProductDetailVm data = productService.getProductDetailBySlug(slug);
        Response<ProductDetailVm> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get by slug successfully");
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/create")
    @PermitAll
    public ResponseEntity<Response<ProductDTO>> createProduct(@RequestBody @Validated CreateProductRequest request) {
        ProductDTO data = productService.createProduct(request);
        Response<ProductDTO> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Created successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateProduct(@RequestBody ProductDTO productDTO) {
        Product product = productService.updateProduct(productDTO);
        return ResponseEntity.ok().body("Update successfully");
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable("id") String id) {
        boolean result = productService.deleteProduct(id);
        return ResponseEntity.ok().body("Delete successfully");
    }

    @GetMapping("/store-front/categories/{categoryId}/products")
    public ResponseEntity<Response<ResponseWithPagination<ProductDTO>>> getProductByCategory(@PathVariable("categoryId") String categoryId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int limit) {
        ResponseWithPagination<ProductDTO> data = productService.getProductByCategoryId(categoryId, page, limit);
        Response<ResponseWithPagination<ProductDTO>> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/store-front/categories/slug/{slug}/products")
    public ResponseEntity<Response<ResponseWithPagination<ProductDTO>>> getProductByCategorySlug(@PathVariable("slug") String slug, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int limit) {
        ResponseWithPagination<ProductDTO> data = productService.getProductByCategorySlug(slug, page, limit);
        Response<ResponseWithPagination<ProductDTO>> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/storefront/products/featured")
    public ResponseEntity<Response<List<?>>> getFeaturedProducts(
            @RequestParam(value = "page", defaultValue = "0", required = false) int page,
            @RequestParam(value = "limit", defaultValue = "10", required = false) int limit
    ) {
//        ResponseWithPagination<?> data = productService.getListFeaturedProducts(page, limit);
//        Response<ResponseWithPagination<ProductDTO>> response = new Response<>();
//        response.setData(data);
//        response.setStatus(ResponseStatus.STATUS_SUCCESS);
//        response.setMessage("Get successfully");
//        return ResponseEntity.ok().body(response);
        return null;
    }

    @GetMapping("/search")
    public ResponseEntity<Response<List<ProductDTO>>> searchProduct(@RequestParam String search) {
        List<ProductDTO> result = productService.searchProduct(search);
        Response<List<ProductDTO>> response = new Response<>();
        response.setData(result);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/sold/{id}")
    public ResponseEntity<Response<Long>> getSoldByProduct(@PathVariable("id") String productId) {
        Long result = productService.getSoldByProduct(productId);
        Response<Long> response = new Response<>();
        response.setData(result);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get successfully");
        return ResponseEntity.ok().body(response);
    }

//    @GetMapping("/get-variant-attribute")
//    public ResponseEntity<Response<List<ProductVariantAttributeValueDTO>>> getVariantAttributeValueByProduct(@RequestParam("product") String productId) {
//        List<ProductVariantAttributeValueDTO> result = productService.getVariantAttributeValueByProduct(productId);
//        Response<List<ProductVariantAttributeValueDTO>> response = new Response<>();
//        response.setData(result);
//        response.setStatus(ResponseStatus.STATUS_SUCCESS);
//        response.setMessage("Get successfully");
//        return ResponseEntity.ok().body(response);
//    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Get product variations by parent id successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ProductVariantVm.class)))),
            @ApiResponse(responseCode = "400", description = "Bad request",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Error.class))),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Error.class)))
    })
    @GetMapping({"/storefront/product-variations/{parentId}", "/backoffice/product-variations/{parentId}"})
    public ResponseEntity<List<ProductVariantVm>> getProductVariationsByParentId(@PathVariable("parentId") String parentId) {
        return ResponseEntity.ok(productService.getProductVariationsByParentId(parentId));
    }

    @GetMapping("/{id}/variants/attributes/detail")
    public ResponseEntity<Response<List<VariantValueResponse>>> getVariantAttributeValueDetail(@PathVariable("id") String productId) {
//        List<VariantValueResponse> result = productService.getVariantAttributeValueByProduct(productId);
        List<VariantValueResponse> result = new ArrayList<>();

        List<OptionValue> options = new ArrayList<>();
        options.add(new OptionValue("Đen", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRGzIvyt_xy57h1p6OstQpVbRZs6OtDDKpiOw&s", 1));
        options.add(new OptionValue("Trắng", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSc0wRIkUneSueNY53rOkf0DskgaL7i8bzThQ&s", 2));
        options.add(new OptionValue("Xám", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSc0wRIkUneSueNY53rOkf0DskgaL7i8bzThQ&s", 3));
        options.add(new OptionValue("Vàng", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSc0wRIkUneSueNY53rOkf0DskgaL7i8bzThQ&s", 4));
        options.add(new OptionValue("Hồng", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSc0wRIkUneSueNY53rOkf0DskgaL7i8bzThQ&s", 5));

        VariantValueResponse variantValueResponse = new VariantValueResponse("Màu sắc", options);

        List<OptionValue> options1 = new ArrayList<>();
        options1.add(new OptionValue("128GB", null, 1));
        options1.add(new OptionValue("256GB", null, 2));
        options1.add(new OptionValue("512GB", null, 3));
        options1.add(new OptionValue("1TB", null, 4));

        VariantValueResponse variantValueResponse1 = new VariantValueResponse("Dung lượng", options1);

        result.add(variantValueResponse);
        result.add(variantValueResponse1);

        Response<List<VariantValueResponse>> response = new Response<>();
        response.setData(result);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping({"/storefront/product-option-combinations/{productId}/values"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ProductOptionCombinationGetVm.class))),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(schema = @Schema(implementation = Error.class))),
    })
    public ResponseEntity<Response<List<ProductOptionCombinationGetVm>>> listProductOptionCombinationOfProduct(@PathVariable("productId") String productId) {
        List<ProductOptionCombinationGetVm> result = variantOptionService.getVariantOptionCombinationByProduct(productId);
        Response<List<ProductOptionCombinationGetVm>> response = new Response<>();
        response.setData(result);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/storefront/product-option-values/{productId}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(
                            implementation = ProductOptionValueGetVm.class))),
            @ApiResponse(responseCode = "404", description = "Not found",
                    content = @Content(schema = @Schema(implementation = Error.class))),
    })
    public ResponseEntity<Response<List<ProductOptionValueGetVm>>> listProductOptionValueOfProduct(@PathVariable("productId") String productId) {
        List<ProductOptionValueGetVm> result = variantOptionService.getVariantOptionValueByProduct(productId);
        Response<List<ProductOptionValueGetVm>> response = new Response<>();
        response.setData(result);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get successfully");
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/filter")
    public ResponseEntity<Response<ResponseWithPagination<ProductDTO>>> getProductWithFilter(
            @RequestParam(value = "page", defaultValue = "0", required = false) int page,
            @RequestParam(value = "limit", defaultValue = "5", required = false) int limit,
            @RequestParam(value = "productName", defaultValue = "", required = false) String productName,
            @RequestParam(value = "category", defaultValue = "", required = false) String category,
            @RequestParam(value = "brand", defaultValue = "", required = false) String brand,
            @RequestParam(value = "rating", defaultValue = "", required = false) int rating,
            @RequestParam(value = "startPrice", defaultValue = "", required = false) Double startPrice,
            @RequestParam(value = "endPrice", defaultValue = "", required = false) Double endPrice
    ) {
        ResponseWithPagination<ProductDTO> result = productService.getProductWithFilter(page, limit, productName, category, brand, rating, startPrice, endPrice);
        Response<ResponseWithPagination<ProductDTO>> response = new Response<>();
        response.setData(result);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Get successfully");
        return ResponseEntity.ok().body(response);
    }

    @QueryMapping(value = "product")
    List<Product> products() {
        return productRepository.findAll();
    }
}
