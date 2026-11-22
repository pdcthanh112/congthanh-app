package com.congthanh.productservice.adapter.in.web;

import com.congthanh.commonservice.model.response.Response;
import com.congthanh.productservice.application.dto.ProductImageDTO;
import com.congthanh.productservice.application.port.in.ProductImageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.congthanh.productservice.adapter.in.web.ProductAttributeController.ok;

@RestController
@RequestMapping("/api/ecommerce/product-image")
@Tag(name = "Product image API", description = "Product image API in CongThanhApp - Ecommerce")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    @GetMapping("/getByProduct")
    public ResponseEntity<Response<List<ProductImageDTO>>> getImageByProduct(@RequestParam("product") String productId) {
        return ResponseEntity.ok(ok(productImageService.getImageByProduct(productId), "Get successfully"));
    }

    @PostMapping("/create")
    public ResponseEntity<Response<ProductImageDTO>> addProductImage(@RequestBody ProductImageDTO productImageDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(productImageService.addProductImage(productImageDTO), "Create successfully"));
    }

    @DeleteMapping("/{productId}/{imageId}")
    public ResponseEntity<Void> deleteProductImage(@PathVariable("productId") String productId, @PathVariable("imageId") Long imageId) {
        productImageService.deleteProductImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
