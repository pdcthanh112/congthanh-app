package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.domain.model.ProductAttributeValue;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.ProductAttribute;
import com.congthanh.productservice.domain.exception.NotFoundException;
import com.congthanh.productservice.application.mapper.ProductAttributeValueMapper;
import com.congthanh.productservice.application.dto.request.ProductAttributeValueRequest;
import com.congthanh.productservice.application.port.out.productAtrributeValue.ProductAttributeValueRepository;
import com.congthanh.productservice.application.port.out.product.ProductRepository;
import com.congthanh.productservice.application.port.out.productAttribute.ProductAttributeRepository;
import com.congthanh.productservice.application.port.in.ProductAttributeValueService;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAttributeValueServiceImpl implements ProductAttributeValueService {

    private final ProductAttributeValueRepository productAttributeValueRepository;

    private final ProductAttributeRepository productAttributeRepository;

    private final ProductRepository productRepository;

    private final ApplicationEventPublisher events;

    @Override
    public List<ProductAttributeValueDTO> getAttributeByProduct(String productId) {
        List<Tuple> data = productAttributeValueRepository.getAttributeOfProduct(productId);
        if (data == null) {
            return null;
        }
        List<ProductAttributeValueDTO> result = new ArrayList<>();
        for (Tuple item : data) {
            ProductAttributeValueDTO valueDTO = ProductAttributeValueDTO.builder()
                    .id(item.get("id", Long.class))
//                    .attribute(ProductAttributeDTO.builder()
//                            .id(item.get("attributeId", Long.class))
//                            .name(item.get("name", String.class))
//                            .build())
                    .product(item.get("product", String.class))
                    .value(item.get("value", String.class))
                    .build();
            result.add(valueDTO);
        }
        return result;
    }

    @Override
    @Transactional
    public ProductAttributeValueDTO createAttributeValue(ProductAttributeValueRequest request) {
        ProductAttribute attribute = productAttributeRepository.findById(request.getAttribute()).orElseThrow(() -> new NotFoundException("attribute not found"));
        Product product = productRepository.findById(request.getProduct()).orElseThrow(() -> new NotFoundException("product not found"));
        ProductAttributeValue productAttributeValue = ProductAttributeValue.builder()
                .productAttribute(attribute)
                .product(product)
                .value(request.getValue())
                .build();
        ProductAttributeValue result = productAttributeValueRepository.save(productAttributeValue);
        events.publishEvent(ProductServiceImpl.changed(product)); // outbox + read model trang sản phẩm
        return ProductAttributeValueMapper.mapProductAttributeValueEntityToDTO(result);
    }

    @Override
    @Transactional
    public ProductAttributeValueDTO updateAttributeValue(ProductAttributeValueRequest request) {
        ProductAttributeValue productAttributeValue = productAttributeValueRepository.findProductAttributeValueOfProduct(request.getAttribute(), request.getProduct());
        if (productAttributeValue == null) {
            throw new NotFoundException("attribute value not found");
        }
        productAttributeValue.setValue(request.getValue());
        ProductAttributeValue result = productAttributeValueRepository.save(productAttributeValue);
        events.publishEvent(ProductServiceImpl.changed(productAttributeValue.getProduct()));
        return ProductAttributeValueMapper.mapProductAttributeValueEntityToDTO(result);
    }
}
