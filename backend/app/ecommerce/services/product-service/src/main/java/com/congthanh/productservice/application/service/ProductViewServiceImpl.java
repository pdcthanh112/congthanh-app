package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.productservice.application.dto.ProductViewDTO;
import com.congthanh.productservice.application.port.in.ProductViewService;
import com.congthanh.productservice.application.port.out.ProductViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductViewServiceImpl implements ProductViewService {

    private final ProductViews views;
    private final Clock clock;

    @Override
    public long getTotalViewOfProduct(String productId) {
        return views.count(productId);
    }

    @Override
    public ProductViewDTO addProductView(String productId, String customerId) {
        if (productId == null || productId.isBlank() || customerId == null || customerId.isBlank()) {
            throw new BadRequestException("productId and customerId are required");
        }
        Instant now = clock.instant();
        long id = views.record(productId, customerId, now);
        return ProductViewDTO.builder().id(id).productId(productId).customerId(customerId).viewedAt(now.toEpochMilli()).build();
    }
}
