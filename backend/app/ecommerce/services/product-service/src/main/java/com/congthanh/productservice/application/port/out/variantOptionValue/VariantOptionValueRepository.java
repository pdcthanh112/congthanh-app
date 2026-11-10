package com.congthanh.productservice.application.port.out.variantOptionValue;

import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.VariantOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VariantOptionValueRepository extends JpaRepository<VariantOptionValue, Long> {

    List<VariantOptionValue> findAllByProduct(Product product);
}
