package com.congthanh.productservice.application.service;

import com.congthanh.productservice.domain.exception.NotFoundException;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionCombinationGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionValueGetVm;
import com.congthanh.productservice.application.port.out.product.ProductRepository;
import com.congthanh.productservice.application.port.out.variantOptionCombination.VariantOptionCombinationRepository;
import com.congthanh.productservice.application.port.out.variantOptionValue.VariantOptionValueRepository;
import com.congthanh.productservice.application.port.in.VariantOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VariantOptionServiceImpl implements VariantOptionService {

    private final ProductRepository productRepository;

    private final VariantOptionValueRepository productOptionValueRepository;

    private final VariantOptionCombinationRepository productOptionCombinationRepository;

    @Override
    public List<ProductOptionValueGetVm> getVariantOptionValueByProduct(String productId) {
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new NotFoundException("Not found"));
        List<ProductOptionValueGetVm> productVariations = productOptionValueRepository
                .findAllByProduct(product).stream()
                .map(ProductOptionValueGetVm::fromModel)
                .toList();
        return productVariations;
    }

    @Override
    public List<ProductOptionCombinationGetVm> getVariantOptionCombinationByProduct(String productId) {
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new NotFoundException("Not found"));

        List<ProductOptionCombinationGetVm> productOptionCombinationGetVms = productOptionCombinationRepository
                .findAllByParentProductId(product.getId()).stream()
                .map(ProductOptionCombinationGetVm::fromModel)
                .toList();
        return new ArrayList<>(productOptionCombinationGetVms
                .stream().collect(Collectors.toMap(
                        p -> Arrays.asList(p.productOptionId(), p.productOptionValue()),
                        p -> p, (existing, replacement) -> existing
                )).values());
    }
}
