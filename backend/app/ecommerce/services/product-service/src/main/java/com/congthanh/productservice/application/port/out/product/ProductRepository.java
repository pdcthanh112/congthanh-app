package com.congthanh.productservice.application.port.out.product;

import com.congthanh.productservice.domain.model.Product;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Transactional
public interface ProductRepository extends JpaRepository<Product, String>, ProductCustomRepository {

    Optional<Product> findProductBySlug(String slug);

    Optional<Product> findByName(String name);

    Page<Product> findByCategory(String categoryId, Pageable pageable);
//
//  Page<Product> findBySubcategoryId(Long subcategoryId, Pageable pageable);

    @Modifying
    @Query("UPDATE Product p SET p.status = com.congthanh.productservice.domain.model.enums.ProductStatus.DELETED WHERE p.id = ?1")
    int softDelete(String id);

    default boolean deleteProduct(String id) {
        return softDelete(id) > 0;
    }

}
