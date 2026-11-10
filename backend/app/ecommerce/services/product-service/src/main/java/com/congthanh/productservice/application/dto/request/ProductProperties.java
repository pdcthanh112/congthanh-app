package com.congthanh.productservice.application.dto.request;

import java.math.BigDecimal;
import java.util.List;

public interface ProductProperties {

    Long id();

    String name();

    String slug();

    String sku();

    String gtin();

    BigDecimal price();

    Long thumbnail();

    List<Long> image();
}
