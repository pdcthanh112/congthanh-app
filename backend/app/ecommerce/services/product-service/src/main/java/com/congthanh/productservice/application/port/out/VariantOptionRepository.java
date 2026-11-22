package com.congthanh.productservice.application.port.out;

import com.congthanh.productservice.domain.model.VariantOption;

import java.util.List;
import java.util.Optional;

public interface VariantOptionRepository {

    Optional<VariantOption> findById(long id);

    Optional<VariantOption> findByNameIgnoreCase(String name);

    /** Theo tên, không phân biệt hoa thường. */
    List<VariantOption> findAll();

    void save(VariantOption option);
}
