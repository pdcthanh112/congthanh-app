package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.dto.ProductViewDTO;
import com.congthanh.productservice.domain.model.ProductView;
import com.congthanh.productservice.application.mapper.ProductViewMapper;
import com.congthanh.productservice.application.port.out.productView.ProductViewRepository;
import com.congthanh.productservice.application.port.in.ProductViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductViewServiceImpl implements ProductViewService {

    private final ProductViewRepository productViewRepository;

    @Override
    public Long getTotalViewOfProduct(String productId) {
        return productViewRepository.getTotalViewOfProduct(productId);
    }

    @Override
    public ProductViewDTO addProductView(String productId, String customerId) {
        ProductView productView = ProductView.builder()
                .productId(productId)
                .viewBy(customerId)
                .viewedAt(Instant.now())
                .build();
        ProductView result = productViewRepository.save(productView);
        return ProductViewMapper.mapProductViewEntityToDTO(result);
    }
}
