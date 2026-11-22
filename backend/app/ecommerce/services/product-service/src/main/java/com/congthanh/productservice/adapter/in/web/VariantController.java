package com.congthanh.productservice.adapter.in.web;

import com.congthanh.commonservice.model.response.Response;
import com.congthanh.productservice.application.dto.request.CreateVariantRequest;
import com.congthanh.productservice.application.dto.viewmodel.ProductVariantVm;
import com.congthanh.productservice.application.dto.viewmodel.VariantOptionVm;
import com.congthanh.productservice.application.port.in.VariantOptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.congthanh.productservice.adapter.in.web.ProductAttributeController.ok;

@RestController
@RequestMapping("/api/ecommerce")
@Tag(name = "Variant API")
@RequiredArgsConstructor
class VariantController {

    private final VariantOptionService variants;

    record CreateOptionRequest(@NotBlank String name) {
    }

    @GetMapping("/variant-options")
    ResponseEntity<Response<List<VariantOptionVm>>> options() {
        return ResponseEntity.ok(ok(variants.getOptions(), "Get successfully"));
    }

    @PostMapping("/variant-options")
    ResponseEntity<Response<VariantOptionVm>> createOption(@RequestBody @Valid CreateOptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(variants.createOption(request.name()), "Created successfully"));
    }

    @PostMapping("/products/{parentId}/variants")
    ResponseEntity<Response<ProductVariantVm>> createVariant(@PathVariable("parentId") String parentId,
                                                             @RequestBody @Valid CreateVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ok(variants.createVariant(parentId, request), "Created successfully"));
    }
}
