package com.congthanh.productservice.application.port.out.productCategory;

import com.congthanh.productservice.domain.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long>, ProductCategoryCustomRepository {

    ProductCategory findProductCategoriesByCategoryIdAndProductId(String categoryId, String productId);

}
