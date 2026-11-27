package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.productservice.application.port.out.AttributeGroupRepository;
import com.congthanh.productservice.domain.model.AttributeGroup;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
class AttributeGroupPersistenceAdapter implements AttributeGroupRepository {

    private final AttributeGroupJpaRepository jpa;

    AttributeGroupPersistenceAdapter(AttributeGroupJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<AttributeGroup> findById(long id) {
        return jpa.findById(id).map(AttributeGroupPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<AttributeGroup> findByNameIgnoreCase(String name) {
        return jpa.findByNameIgnoreCase(name).map(AttributeGroupPersistenceAdapter::toDomain);
    }

    @Override
    public List<AttributeGroup> findAll() {
        return jpa.findAllByOrderByDisplayOrderAscNameAsc().stream().map(AttributeGroupPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AttributeGroup g) {
        AttributeGroupEntity e = jpa.findById(g.id()).orElseGet(() -> AttributeGroupEntity.builder().id(g.id()).build());
        e.setName(g.name());
        e.setDisplayOrder(g.displayOrder());
        try {
            jpa.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("attribute group name already exists", ex);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(long id) {
        jpa.deleteById(id);
        jpa.flush();
    }

    private static AttributeGroup toDomain(AttributeGroupEntity e) {
        return AttributeGroup.rehydrate(e.getId(), e.getName(), e.getDisplayOrder());
    }
}
