package com.congthanh.productservice.application.port.out.variantOptionCombination;

import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.VariantOptionCombination;

import java.util.List;

public interface VariantOptionCombinationCustomRepository {

    List<VariantOptionCombination> findAllByProduct(Product product);
}
