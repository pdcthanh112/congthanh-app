package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.AttributeGroupDto;
import com.congthanh.productservice.application.dto.request.AttributeGroupRequest;

import java.util.List;

/** Quản lý nhóm hiển thị của thông số (ADR 0033). Tên nhóm unique, không phân biệt hoa thường. */
public interface AttributeGroupService {

    List<AttributeGroupDto> list();

    AttributeGroupDto create(AttributeGroupRequest request);

    AttributeGroupDto update(long id, AttributeGroupRequest request);

    /** Xoá nhóm; thuộc tính trong nhóm thành "không nhóm". Không có nhóm thì 404. */
    void delete(long id);
}
