package com.congthanh.productservice.adapter.in.web;

import com.congthanh.commonservice.model.response.Response;
import com.congthanh.productservice.application.dto.AttributeGroupDto;
import com.congthanh.productservice.application.dto.request.AttributeGroupRequest;
import com.congthanh.productservice.application.port.in.AttributeGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.congthanh.productservice.adapter.in.web.ProductAttributeController.ok;

@RestController
@RequestMapping("/api/ecommerce/product-attribute/groups")
@Tag(name = "Product Attribute API")
@RequiredArgsConstructor
class AttributeGroupController {

    private final AttributeGroupService attributeGroupService;

    @GetMapping
    @Operation(summary = "List attribute groups in display order")
    ResponseEntity<Response<List<AttributeGroupDto>>> list() {
        return ResponseEntity.ok(ok(attributeGroupService.list(), "Get successfully"));
    }

    @PostMapping
    @Operation(summary = "Create an attribute group")
    ResponseEntity<Response<AttributeGroupDto>> create(@Valid @RequestBody AttributeGroupRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(attributeGroupService.create(body), "Created successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename or reorder an attribute group")
    ResponseEntity<Response<AttributeGroupDto>> update(@PathVariable("id") long id, @Valid @RequestBody AttributeGroupRequest body) {
        return ResponseEntity.ok(ok(attributeGroupService.update(id, body), "Updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an attribute group", description = "Attributes in the group become ungrouped")
    ResponseEntity<Void> delete(@PathVariable("id") long id) {
        attributeGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
