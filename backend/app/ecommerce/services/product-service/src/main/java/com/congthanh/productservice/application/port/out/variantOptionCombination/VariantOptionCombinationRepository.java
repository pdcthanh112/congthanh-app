package com.congthanh.productservice.application.port.out.variantOptionCombination;

import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.VariantOptionCombination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VariantOptionCombinationRepository extends JpaRepository<VariantOptionCombination, Long>, VariantOptionCombinationCustomRepository {

    //    @Query("select e from VariantOptionCombination e where e.product.parent.id = ?1")
    @Query(value = "select * from variant_option_combination where product_id =  ?1", nativeQuery = true)
    List<VariantOptionCombination> findAllByParentProductId(String parentProductId);

}
