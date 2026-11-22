package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.productservice.application.port.out.VariantOptionRepository;
import com.congthanh.productservice.domain.model.VariantOption;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
class VariantOptionPersistenceAdapter implements VariantOptionRepository {

    private final VariantOptionJpaRepository jpa;

    VariantOptionPersistenceAdapter(VariantOptionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<VariantOption> findById(long id) {
        return jpa.findById(id).map(e -> new VariantOption(e.getId(), e.getName()));
    }

    @Override
    public Optional<VariantOption> findByNameIgnoreCase(String name) {
        return jpa.findByNameIgnoreCase(name).map(e -> new VariantOption(e.getId(), e.getName()));
    }

    @Override
    public List<VariantOption> findAll() {
        return jpa.findAll().stream()
                .map(e -> new VariantOption(e.getId(), e.getName()))
                .sorted(Comparator.comparing(VariantOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(VariantOption option) {
        try {
            jpa.saveAndFlush(VariantOptionEntity.builder().id(option.id()).name(option.name()).build());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("option already exists: " + option.name(), ex);
        }
    }
}
