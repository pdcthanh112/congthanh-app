package com.congthanh.productservice.adapter.in.web;

import com.congthanh.commonservice.model.response.Response;
import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.request.ProductAttributeValueRequest;
import com.congthanh.productservice.application.port.in.ProductAccessUseCase;
import com.congthanh.productservice.application.port.in.ProductAttributeValueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.congthanh.productservice.adapter.in.web.ProductAttributeController.ok;

@RestController
@RequestMapping("/api/ecommerce/attribute-value")
@RequiredArgsConstructor
public class ProductAttributeValueController {

    private final ProductAttributeValueService productAttributeValueService;
    private final ProductAccessUseCase access;

    @GetMapping("/getByProduct")
    public ResponseEntity<Response<List<ProductAttributeValueDTO>>> getAttributeByProduct(@RequestParam("product") String productId) {
        return ResponseEntity.ok(ok(productAttributeValueService.getAttributeByProduct(productId), "get successfully"));
    }

    @PostMapping("/create")
    public ResponseEntity<Response<List<ProductAttributeValueDTO>>> createAttributeValue(@RequestBody @Valid ProductAttributeValueRequest request,
                                                                                   Authentication auth) {
        access.requireModifiable(request.getProduct(), Actors.of(auth));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ok(productAttributeValueService.createAttributeValue(request), "Created successfully"));
    }

    @PutMapping("/update")
    public ResponseEntity<Response<List<ProductAttributeValueDTO>>> updateAttributeValue(@RequestBody @Valid ProductAttributeValueRequest request,
                                                                                   Authentication auth) {
        access.requireModifiable(request.getProduct(), Actors.of(auth));
        return ResponseEntity.ok(ok(productAttributeValueService.updateAttributeValue(request), "updated successfully"));
    }

    @DeleteMapping("/{productId}/{attributeId}")
    public ResponseEntity<Void> deleteAttributeValue(@PathVariable("productId") String productId,
                                                     @PathVariable("attributeId") Long attributeId,
                                                     Authentication auth) {
        access.requireModifiable(productId, Actors.of(auth));
        productAttributeValueService.deleteAttributeValue(productId, attributeId);
        return ResponseEntity.noContent().build();
    }
}
