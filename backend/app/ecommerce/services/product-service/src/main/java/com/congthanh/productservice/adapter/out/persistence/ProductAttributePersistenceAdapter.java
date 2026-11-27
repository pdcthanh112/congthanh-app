package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.productservice.application.port.out.ProductAttributeRepository;
import com.congthanh.productservice.domain.model.ProductAttribute;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
class ProductAttributePersistenceAdapter implements ProductAttributeRepository {

    private final ProductAttributeJpaRepository jpa;

    ProductAttributePersistenceAdapter(ProductAttributeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductAttribute> findById(long id) {
        return jpa.findById(id).map(ProductAttributePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductAttribute> findByCode(String code) {
        return jpa.findByCode(code).map(ProductAttributePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductAttribute> findByNameIgnoreCase(String name) {
        return jpa.findByNameIgnoreCase(name).map(ProductAttributePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
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
        e.setGroupId(a.groupId());
        applyOptions(a, e);
        try {
            jpa.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("attribute name or code already exists", ex);
        }
    }

    /** Giữ dòng cũ khi giá trị còn (so không phân biệt hoa thường), cập nhật chính tả và thứ tự, thêm/bỏ phần chênh. */
    private static void applyOptions(ProductAttribute a, ProductAttributeEntity e) {
        Map<String, Integer> wanted = new HashMap<>();
        for (int i = 0; i < a.options().size(); i++) {
            wanted.put(a.options().get(i).toLowerCase(Locale.ROOT), i);
        }
        e.getOptions().removeIf(o -> !wanted.containsKey(o.getValue().toLowerCase(Locale.ROOT)));
        Set<String> present = new HashSet<>();
        for (ProductAttributeOptionEntity o : e.getOptions()) {
            String key = o.getValue().toLowerCase(Locale.ROOT);
            int index = wanted.get(key);
            o.setValue(a.options().get(index));
            o.setDisplayOrder(index);
            present.add(key);
        }
        for (int i = 0; i < a.options().size(); i++) {
            String value = a.options().get(i);
            if (!present.contains(value.toLowerCase(Locale.ROOT))) {
                e.getOptions().add(ProductAttributeOptionEntity.builder()
                        .id(Snowflake.nextId()).attribute(e).value(value).displayOrder(i).build());
            }
        }
    }

    private static ProductAttribute toDomain(ProductAttributeEntity e) {
        List<String> options = e.getOptions().stream()
                .sorted(Comparator.comparingInt(ProductAttributeOptionEntity::getDisplayOrder))
                .map(ProductAttributeOptionEntity::getValue)
                .toList();
        return ProductAttribute.rehydrate(e.getId(), e.getCode(), e.getName(), e.getDataType(), e.getUnit(),
                e.isFilterable(), e.isSearchable(), e.getDisplayOrder(), e.getGroupId(), options);
    }
}
