package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.request.ProductAttributeValueRequest;
import com.congthanh.productservice.application.port.in.ProductAttributeValueService;
import com.congthanh.productservice.application.port.out.ProductAttributeRepository;
import com.congthanh.productservice.application.port.out.ProductQueries;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.ProductAttribute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAttributeValueServiceImpl implements ProductAttributeValueService {

    private final ProductRepository products;
    private final ProductAttributeRepository attributes;
    private final ProductQueries queries;

    @Override
    public List<ProductAttributeValueDTO> getAttributeByProduct(String productId) {
        return queries.attributeValues(productId);
    }

    @Override
    @Transactional
    public ProductAttributeValueDTO createAttributeValue(ProductAttributeValueRequest request) {
        ProductAttribute attribute = requireAttribute(request.getAttribute());
        Product product = requireProduct(request.getProduct());
        product.addAttribute(attribute.valueOf(request.getValue()));
        products.save(product);
        return valueOf(product.id(), attribute.id());
    }

    @Override
    @Transactional
    public ProductAttributeValueDTO updateAttributeValue(ProductAttributeValueRequest request) {
        ProductAttribute attribute = requireAttribute(request.getAttribute());
        Product product = requireProduct(request.getProduct());
        product.changeAttribute(attribute.valueOf(request.getValue()));
        products.save(product);
        return valueOf(product.id(), attribute.id());
    }

    @Override
    @Transactional
    public void deleteAttributeValue(String productId, Long attributeId) {
        Product product = requireProduct(productId);
        product.removeAttribute(attributeId);
        products.save(product);
    }

    private ProductAttributeValueDTO valueOf(String productId, long attributeId) {
        return queries.attributeValues(productId).stream()
                .filter(v -> v.getAttributeId() == attributeId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("attribute value " + attributeId + " was not saved"));
    }

    private ProductAttribute requireAttribute(Long id) {
        return attributes.findById(id).orElseThrow(() -> new NotFoundException("attribute not found"));
    }

    private Product requireProduct(String id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("product not found: " + id));
    }
}
