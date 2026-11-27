package com.congthanh.productservice.application.dto;

import com.congthanh.productservice.domain.model.enums.AttributeDataType;

import java.util.List;

/**
 * Một dòng trong mẫu thông số của danh mục, kèm định nghĩa thuộc tính để form của seller dựng ô nhập ngay
 * (kiểu, đơn vị, giá trị cho phép, nhóm).
 */
public record CategoryAttributeDto(Long attributeId, String code, String name, AttributeDataType dataType, String unit,
                                   List<String> options, Long groupId, String groupName, boolean required,
                                   int displayOrder) {
}
