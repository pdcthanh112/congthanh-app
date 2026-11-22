package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.request.CreateVariantRequest;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionCombinationGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionValueGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVariantVm;
import com.congthanh.productservice.application.dto.viewmodel.VariantOptionVm;

import java.util.List;

public interface VariantOptionService {

    List<VariantOptionVm> getOptions();

    VariantOptionVm createOption(String name);

    /** Giá trị được phép của các trục trên sản phẩm gốc. */
    List<ProductOptionValueGetVm> getVariantOptionValueByProduct(String productId);

    /** Các giá trị đang được ít nhất một variant của sản phẩm gốc dùng (mỗi giá trị một lần). */
    List<ProductOptionCombinationGetVm> getVariantOptionCombinationByProduct(String productId);

    ProductVariantVm createVariant(String parentId, CreateVariantRequest request);
}
