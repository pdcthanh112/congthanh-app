package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.productservice.application.dto.ProductImageDTO;
import com.congthanh.productservice.application.port.in.ProductImageService;
import com.congthanh.productservice.application.port.out.ProductQueries;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.ProductImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductRepository products;
    private final ProductQueries queries;

    @Override
    public List<ProductImageDTO> getImageByProduct(String productId) {
        return queries.images(productId);
    }

    @Override
    @Transactional
    public ProductImageDTO addProductImage(ProductImageDTO dto) {
        if (dto.getId() == null) {
            throw new BadRequestException("id (media id from media-service) is required");
        }
        if (dto.getProduct() == null || dto.getProduct().isBlank()) {
            throw new BadRequestException("product is required");
        }
        Product product = requireProduct(dto.getProduct());
        product.addImage(new ProductImage(dto.getId(), dto.getImagePath(), dto.getDisplayOrder()), dto.isDefault());
        products.save(product);
        return queries.images(product.id()).stream()
                .filter(i -> Objects.equals(i.getId(), dto.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("image " + dto.getId() + " was not saved"));
    }

    @Override
    @Transactional
    public void deleteProductImage(String productId, Long imageId) {
        Product product = requireProduct(productId);
        product.removeImage(imageId);
        products.save(product);
    }

    private Product requireProduct(String id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("product not found: " + id));
    }
}
