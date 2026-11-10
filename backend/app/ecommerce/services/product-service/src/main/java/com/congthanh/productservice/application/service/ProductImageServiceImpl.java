package com.congthanh.productservice.application.service;

import com.congthanh.productservice.domain.model.ProductImage;
import com.congthanh.productservice.application.dto.ProductImageDTO;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.exception.NotFoundException;
import com.congthanh.productservice.application.mapper.ProductImageMapper;
import com.congthanh.productservice.application.port.out.product.ProductRepository;
import com.congthanh.productservice.application.port.out.productImage.ProductImageRepository;
import com.congthanh.productservice.application.port.in.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductRepository productRepository;

    private final ProductImageRepository productImageRepository;

    @Override
    public List<ProductImageDTO> getImageByProduct(String productId) {
        List<ProductImage> data = productRepository.getImageByProduct(productId);
        List<ProductImageDTO> result = new ArrayList<>();
        for (ProductImage item : data) {
            ProductImageDTO imageDTO = ProductImageMapper.mapProductImageEntityToDTO(item);
            result.add(imageDTO);
        }
        return result;
    }

    @Override
    public ProductImageDTO addProductImage(ProductImageDTO productImageDTO) {
        Product product = productRepository.findById(productImageDTO.getProduct()).orElseThrow(() -> new NotFoundException(("not found")));
        ProductImage image = ProductImage.builder()
                .product(product)
                .imagePath(productImageDTO.getImagePath())
                .build();
        ProductImage result = productImageRepository.save(image);
        return ProductImageMapper.mapProductImageEntityToDTO(result);
    }
}
