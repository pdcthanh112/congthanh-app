package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.productservice.application.dto.CategoryAttributeDto;
import com.congthanh.productservice.application.dto.request.CategoryAttributeRequest;
import com.congthanh.productservice.application.port.in.CategoryAttributeService;
import com.congthanh.productservice.application.port.out.AttributeGroupRepository;
import com.congthanh.productservice.application.port.out.CategoryAttributeRepository;
import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.port.out.ProductAttributeRepository;
import com.congthanh.productservice.domain.model.AttributeGroup;
import com.congthanh.productservice.domain.model.CategoryAttributeTemplate;
import com.congthanh.productservice.domain.model.ProductAttribute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryAttributeServiceImpl implements CategoryAttributeService {

    private final CategoryAttributeRepository templates;
    private final ProductAttributeRepository attributes;
    private final AttributeGroupRepository groups;
    private final CategoryCatalog categoryCatalog;
    private final TransactionTemplate tx;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryAttributeDto> template(String categoryId) {
        return view(templates.find(categoryId));
    }

    @Override
    public List<CategoryAttributeDto> replace(String categoryId, CategoryAttributeRequest request) {
        // Gọi catalog-service (gRPC) trước, ngoài transaction: không giữ transaction DB trong lúc gọi mạng.
        try {
            categoryCatalog.validate(List.of(categoryId));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        List<CategoryAttributeTemplate.Item> items = new ArrayList<>(request.attributes().size());
        for (int i = 0; i < request.attributes().size(); i++) {
            CategoryAttributeRequest.Item r = request.attributes().get(i);
            int order = r.displayOrder() == null ? i : r.displayOrder();
            items.add(new CategoryAttributeTemplate.Item(r.attributeId(), r.required(), order));
        }
        CategoryAttributeTemplate template = new CategoryAttributeTemplate(categoryId, items);
        return tx.execute(status -> save(template));
    }

    private List<CategoryAttributeDto> save(CategoryAttributeTemplate template) {
        Map<Long, ProductAttribute> byId = attributesById();
        for (CategoryAttributeTemplate.Item i : template.items()) {
            if (!byId.containsKey(i.attributeId())) {
                throw new BadRequestException("attribute not found: " + i.attributeId());
            }
        }
        templates.replace(template);
        return view(template, byId);
    }

    private List<CategoryAttributeDto> view(CategoryAttributeTemplate template) {
        return view(template, attributesById());
    }

    private List<CategoryAttributeDto> view(CategoryAttributeTemplate template, Map<Long, ProductAttribute> byId) {
        Map<Long, AttributeGroup> groupsById = groups.findAll().stream()
                .collect(Collectors.toMap(AttributeGroup::id, Function.identity()));
        List<CategoryAttributeDto> out = new ArrayList<>(template.items().size());
        for (CategoryAttributeTemplate.Item i : template.items()) {
            ProductAttribute a = byId.get(i.attributeId());
            if (a == null) {
                continue;
            }
            AttributeGroup g = a.groupId() == null ? null : groupsById.get(a.groupId());
            out.add(new CategoryAttributeDto(a.id(), a.code(), a.name(), a.dataType(), a.unit(), a.options(),
                    a.groupId(), g == null ? null : g.name(), i.required(), i.displayOrder()));
        }
        return List.copyOf(out);
    }

    private Map<Long, ProductAttribute> attributesById() {
        return attributes.findAll().stream().collect(Collectors.toMap(ProductAttribute::id, Function.identity()));
    }
}
