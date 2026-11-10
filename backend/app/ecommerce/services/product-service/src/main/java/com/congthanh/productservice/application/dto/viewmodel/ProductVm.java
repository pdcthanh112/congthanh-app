package com.congthanh.productservice.application.dto.viewmodel;

import com.congthanh.productservice.domain.model.enums.ProductStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductVm {
    private String id;
    private String name;
    private List<CategoryVm> category;
    private String slug;
    private String description;
    private ProductImageVm thumbnail;
    private List<ProductImageVm> image;
    private List<ProductAttribute> attribute;
    private String supplier;
    private String brand;
    //    private List<ProductVariantVm> variant;
    private ProductStatus status;
}


record CategoryVm(
        String id,
        String name) {
}

//record ProductVariantVm(String id,
//                        String product,
//                        String name,
//                        ProductImageVm thumbnail,
//                        String sku,
//                        String gtin,
//                        BigDecimal price,
//                        List<VariantAttribute> attribute,
//                        List<ProductImageVm> image) {
//}

record ProductAttribute(Long id,
                        String name,
                        String value) {
}

record VariantAttribute(Long id,
                        String name,
                        String value) {
}
