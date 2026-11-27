package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.productservice.application.dto.AttributeGroupDto;
import com.congthanh.productservice.application.dto.request.AttributeGroupRequest;
import com.congthanh.productservice.application.port.in.AttributeGroupService;
import com.congthanh.productservice.application.port.out.AttributeGroupRepository;
import com.congthanh.productservice.domain.model.AttributeGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttributeGroupServiceImpl implements AttributeGroupService {

    private final AttributeGroupRepository groups;

    @Override
    @Transactional(readOnly = true)
    public List<AttributeGroupDto> list() {
        return groups.findAll().stream().map(AttributeGroupDto::of).toList();
    }

    @Override
    @Transactional
    public AttributeGroupDto create(AttributeGroupRequest request) {
        AttributeGroup g = AttributeGroup.define(Snowflake.nextId(), request.name(), request.displayOrder());
        requireUnique(g);
        groups.save(g);
        return AttributeGroupDto.of(g);
    }

    @Override
    @Transactional
    public AttributeGroupDto update(long id, AttributeGroupRequest request) {
        AttributeGroup g = require(id);
        g.rename(request.name(), request.displayOrder());
        requireUnique(g);
        groups.save(g);
        return AttributeGroupDto.of(g);
    }

    @Override
    @Transactional
    public void delete(long id) {
        require(id);
        groups.delete(id);
    }

    private AttributeGroup require(long id) {
        return groups.findById(id).orElseThrow(() -> new NotFoundException("attribute group not found: " + id));
    }

    /** Tên unique (DB cũng chặn bằng unique index lower(name)); kiểm trước để trả 409 rõ ràng. */
    private void requireUnique(AttributeGroup g) {
        groups.findByNameIgnoreCase(g.name()).filter(o -> o.id() != g.id()).ifPresent(o -> {
            throw new ConflictException("attribute group name already exists: " + g.name());
        });
    }
}
