package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.productservice.application.dto.ProductAttributeDTO;
import com.congthanh.productservice.application.mapper.ProductAttributeMapper;
import com.congthanh.productservice.application.port.in.ProductAttributeService;
import com.congthanh.productservice.application.port.out.ProductAttributeRepository;
import com.congthanh.productservice.domain.model.ProductAttribute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAttributeServiceImpl implements ProductAttributeService {

    private final ProductAttributeRepository attributes;

    @Override
    public List<ProductAttributeDTO> getAllProductAttribute() {
        return attributes.findAll().stream().map(ProductAttributeMapper::toDto).toList();
    }

    @Override
    @Transactional
    public ProductAttributeDTO createProductAttribute(ProductAttributeDTO dto) {
        ProductAttribute a = ProductAttribute.define(Snowflake.nextId(), spec(dto));
        requireUnique(a);
        attributes.save(a);
        return ProductAttributeMapper.toDto(a);
    }

    @Override
    @Transactional
    public ProductAttributeDTO updateProductAttribute(Long id, ProductAttributeDTO dto) {
        ProductAttribute a = attributes.findById(id).orElseThrow(() -> new NotFoundException("attribute not found"));
        a.redefine(spec(dto), attributes.hasValues(id));
        requireUnique(a);
        attributes.save(a);
        return ProductAttributeMapper.toDto(a);
    }

    /** Tên và code unique (DB cũng chặn); kiểm trước để trả lời rõ ràng. */
    private void requireUnique(ProductAttribute a) {
        attributes.findByNameIgnoreCase(a.name()).filter(o -> o.id() != a.id()).ifPresent(o -> {
            throw new ConflictException("attribute name already exists: " + a.name());
        });
        if (a.code() != null) {
            attributes.findByCode(a.code()).filter(o -> o.id() != a.id()).ifPresent(o -> {
                throw new ConflictException("attribute code already exists: " + a.code());
            });
        }
    }

    private static ProductAttribute.Spec spec(ProductAttributeDTO dto) {
        return new ProductAttribute.Spec(dto.getCode(), dto.getName(), dto.getDataType(), dto.getUnit(), dto.isFilterable(),
                dto.isSearchable(), dto.getDisplayOrder());
    }
}
