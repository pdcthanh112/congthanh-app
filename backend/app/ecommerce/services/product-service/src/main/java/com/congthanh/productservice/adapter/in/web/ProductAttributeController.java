package com.congthanh.productservice.adapter.in.web;

import com.congthanh.commonservice.constants.ResponseStatus;
import com.congthanh.commonservice.model.response.Response;
import com.congthanh.productservice.application.dto.ProductAttributeDTO;
import com.congthanh.productservice.application.port.in.ProductAttributeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ecommerce/product-attribute")
@Tag(name = "Product Attribute API", description = "Product attribute API in CongThanhApp - Ecommerce")
@RequiredArgsConstructor
public class ProductAttributeController {

    private final ProductAttributeService productAttributeService;

    @GetMapping("")
    public ResponseEntity<Response<List<ProductAttributeDTO>>> getAllProductAttributes() {
        return ResponseEntity.ok(ok(productAttributeService.getAllProductAttribute(), "Get successfully"));
    }

    @PostMapping("")
    public ResponseEntity<Response<ProductAttributeDTO>> createProductAttribute(@RequestBody ProductAttributeDTO productAttributeDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ok(productAttributeService.createProductAttribute(productAttributeDTO), "Created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Response<ProductAttributeDTO>> updateProductAttribute(@PathVariable("id") Long id,
                                                                               @RequestBody ProductAttributeDTO productAttributeDTO) {
        return ResponseEntity.ok(ok(productAttributeService.updateProductAttribute(id, productAttributeDTO), "Updated successfully"));
    }

    static <T> Response<T> ok(T data, String message) {
        Response<T> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage(message);
        return response;
    }
}
