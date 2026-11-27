package com.congthanh.productservice.adapter.in.web;

import com.congthanh.commonservice.model.response.Response;
import com.congthanh.productservice.application.dto.CategoryAttributeDto;
import com.congthanh.productservice.application.dto.request.CategoryAttributeRequest;
import com.congthanh.productservice.application.port.in.CategoryAttributeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.congthanh.productservice.adapter.in.web.ProductAttributeController.ok;

@RestController
@RequestMapping("/api/ecommerce/product-attribute/categories/{categoryId}")
@Tag(name = "Product Attribute API")
@RequiredArgsConstructor
class CategoryAttributeController {

    private final CategoryAttributeService templates;

    @GetMapping
    @Operation(summary = "Attribute template of a category", description = "Attributes that apply to products in the category, in form order")
    ResponseEntity<Response<List<CategoryAttributeDto>>> template(@PathVariable("categoryId") String categoryId) {
        return ResponseEntity.ok(ok(templates.template(categoryId), "Get successfully"));
    }

    @PutMapping
    @Operation(summary = "Replace the attribute template of a category")
    ResponseEntity<Response<List<CategoryAttributeDto>>> replace(@PathVariable("categoryId") String categoryId,
                                                                 @Valid @RequestBody CategoryAttributeRequest body) {
        return ResponseEntity.ok(ok(templates.replace(categoryId, body), "Updated successfully"));
    }
}
