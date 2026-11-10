package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.viewmodel.ProductOptionCombinationGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionValueGetVm;

import java.util.List;

public interface VariantOptionService {

    List<ProductOptionValueGetVm> getVariantOptionValueByProduct(String productId);

    List<ProductOptionCombinationGetVm> getVariantOptionCombinationByProduct(String productId);
}
