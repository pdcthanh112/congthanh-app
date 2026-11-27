package com.congthanh.productservice.application.port.out;

import com.congthanh.productservice.domain.model.AttributeGroup;

import java.util.List;
import java.util.Optional;

/** Lưu nhóm hiển thị của thông số (ADR 0033). */
public interface AttributeGroupRepository {

    Optional<AttributeGroup> findById(long id);

    Optional<AttributeGroup> findByNameIgnoreCase(String name);

    /** Theo thứ tự hiển thị rồi tên. */
    List<AttributeGroup> findAll();

    void save(AttributeGroup group);

    /** Xoá nhóm; thuộc tính trong nhóm thành "không nhóm" (FK ON DELETE SET NULL). */
    void delete(long id);
}
