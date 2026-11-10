package com.congthanh.productservice.application.dto.viewmodel;

import com.congthanh.productservice.domain.model.VariantOptionCombination;

public record ProductOptionCombinationGetVm(Long id,
                                            Long productOptionId,
                                            String productOptionValue,
                                            String productOptionName) {
    public static ProductOptionCombinationGetVm fromModel(VariantOptionCombination productOptionCombination) {
        return new ProductOptionCombinationGetVm(
                productOptionCombination.getId(),
                productOptionCombination.getVariantOption().getId(),
                productOptionCombination.getValue(),
                productOptionCombination.getVariantOption().getName()
        );
    }
}
