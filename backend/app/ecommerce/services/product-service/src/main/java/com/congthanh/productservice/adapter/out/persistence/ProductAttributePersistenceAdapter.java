package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.productservice.application.port.out.ProductAttributeRepository;
import com.congthanh.productservice.domain.model.ProductAttribute;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
class ProductAttributePersistenceAdapter implements ProductAttributeRepository {

    private final ProductAttributeJpaRepository jpa;

    ProductAttributePersistenceAdapter(ProductAttributeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ProductAttribute> findById(long id) {
        return jpa.findById(id).map(ProductAttributePersistenceAdapter::toDomain);
    }

    @Override
    public Optional<ProductAttribute> findByCode(String code) {
        return jpa.findByCode(code).map(ProductAttributePersistenceAdapter::toDomain);
    }

    @Override
    public Optional<ProductAttribute> findByNameIgnoreCase(String name) {
        return jpa.findByNameIgnoreCase(name).map(ProductAttributePersistenceAdapter::toDomain);
    }

    @Override
    public List<ProductAttribute> findAll() {
        return jpa.findAllByOrderByDisplayOrderAscNameAsc().stream().map(ProductAttributePersistenceAdapter::toDomain).toList();
    }

    @Override
    public boolean hasValues(long id) {
        return jpa.hasValues(id);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(ProductAttribute a) {
        ProductAttributeEntity e = jpa.findById(a.id()).orElseGet(() -> ProductAttributeEntity.builder().id(a.id()).build());
        e.setCode(a.code());
        e.setName(a.name());
        e.setDataType(a.dataType());
        e.setUnit(a.unit());
        e.setFilterable(a.filterable());
        e.setSearchable(a.searchable());
        e.setDisplayOrder(a.displayOrder());
        try {
            jpa.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("attribute name or code already exists", ex);
        }
    }

    private static ProductAttribute toDomain(ProductAttributeEntity e) {
        return ProductAttribute.rehydrate(e.getId(), e.getCode(), e.getName(), e.getDataType(), e.getUnit(),
                e.isFilterable(), e.isSearchable(), e.getDisplayOrder());
    }
}
