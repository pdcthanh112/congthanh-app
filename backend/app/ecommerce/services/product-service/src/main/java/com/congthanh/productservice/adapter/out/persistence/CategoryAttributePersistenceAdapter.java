package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.productservice.application.port.out.CategoryAttributeRepository;
import com.congthanh.productservice.domain.model.CategoryAttributeTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class CategoryAttributePersistenceAdapter implements CategoryAttributeRepository {

    private final CategoryAttributeJpaRepository jpa;

    CategoryAttributePersistenceAdapter(CategoryAttributeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public CategoryAttributeTemplate find(String categoryId) {
        return new CategoryAttributeTemplate(categoryId, jpa.findByCategoryIdOrderByDisplayOrderAscAttributeIdAsc(categoryId).stream()
                .map(e -> new CategoryAttributeTemplate.Item(e.getAttributeId(), e.isRequired(), e.getDisplayOrder()))
                .toList());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(CategoryAttributeTemplate template) {
        jpa.deleteByCategory(template.categoryId());
        jpa.saveAll(template.items().stream()
                .map(i -> CategoryAttributeEntity.builder()
                        .categoryId(template.categoryId())
                        .attributeId(i.attributeId())
                        .required(i.required())
                        .displayOrder(i.displayOrder())
                        .build())
                .toList());
        jpa.flush();
    }
}
