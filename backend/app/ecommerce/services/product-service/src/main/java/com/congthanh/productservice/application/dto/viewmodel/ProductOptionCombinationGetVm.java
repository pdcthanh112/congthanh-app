package com.congthanh.productservice.application.dto.viewmodel;

/** Một giá trị option đang được ít nhất một variant dùng. {@code id} là id của variant_option_value. */
public record ProductOptionCombinationGetVm(Long id,
                                            Long productOptionId,
                                            String productOptionValue,
                                            String productOptionName) {
}
