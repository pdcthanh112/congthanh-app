package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.dto.ProductAttributeDTO;
import com.congthanh.productservice.domain.model.ProductAttribute;
import com.congthanh.productservice.application.mapper.ProductAttributeMapper;
import com.congthanh.productservice.application.dto.request.ProductAttributeValueRequest;
import com.congthanh.productservice.application.port.out.productAttribute.ProductAttributeRepository;
import com.congthanh.productservice.application.port.in.ProductAttributeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAttributeServiceImpl implements ProductAttributeService {

    private final ProductAttributeRepository productAttributeRepository;

    @Override
    public List<ProductAttributeDTO> getAllProductAttribute() {
        return productAttributeRepository.findAll().stream().map(item -> ProductAttributeDTO.builder().id(item.getId()).name(item.getName()).build()).toList();
    }

    @Override
    public ProductAttributeDTO createProductAttribute(ProductAttributeDTO productAttributeDTO) {
        ProductAttribute productAttribute = ProductAttribute.builder()
                .name(productAttributeDTO.getName())
                .build();
        ProductAttribute result = productAttributeRepository.save(productAttribute);
        return ProductAttributeMapper.mapProductAttributeEntityToDTO(result);
    }

    @Override
    public void addAttributeToProduct(List<ProductAttributeValueRequest> request, String productId) {

    }

}
