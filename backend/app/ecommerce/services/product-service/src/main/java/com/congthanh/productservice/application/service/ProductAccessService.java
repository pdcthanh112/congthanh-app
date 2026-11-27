package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.commonservice.exception.PermissionException;
import com.congthanh.productservice.application.dto.Actor;
import com.congthanh.productservice.application.port.in.ProductAccessUseCase;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.domain.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductAccessService implements ProductAccessUseCase {

    private final ProductRepository products;

    @Override
    @Transactional(readOnly = true)
    public void requireModifiable(String productId, Actor actor) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("product not found: " + productId));
        if (actor.admin()) {
            return;
        }
        if (!product.isOwnedBy(actor.subject())) {
            throw new PermissionException("product " + productId + " belongs to another seller");
        }
    }
}
