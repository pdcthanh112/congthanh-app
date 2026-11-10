package com.congthanh.productservice.adapter.in.web;

import com.congthanh.productservice.application.dto.constant.ResponseStatus;
import com.congthanh.productservice.application.dto.ProductAttributeDTO;
import com.congthanh.productservice.application.dto.response.Response;
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
        List<ProductAttributeDTO> data = productAttributeService.getAllProductAttribute();
        Response<List<ProductAttributeDTO>> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Created successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/create")
    public ResponseEntity<Response<ProductAttributeDTO>> createProductAttribute(@RequestBody ProductAttributeDTO productAttributeDTO) {
        ProductAttributeDTO data = productAttributeService.createProductAttribute(productAttributeDTO);
        Response<ProductAttributeDTO> response = new Response<>();
        response.setData(data);
        response.setStatus(ResponseStatus.STATUS_SUCCESS);
        response.setMessage("Created successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
