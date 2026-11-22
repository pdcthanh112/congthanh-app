package com.congthanh.productservice.application.dto.viewmodel;

public record ProductOptionValueGetVm(Long id,
                                      Long productOptionId,
                                      String productOptionValue,
                                      String productOptionName,
                                      int displayOrder) {
}
