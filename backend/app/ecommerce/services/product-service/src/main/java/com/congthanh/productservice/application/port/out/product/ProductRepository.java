package com.congthanh.productservice.application.port.out.product;

import com.congthanh.productservice.domain.model.Product;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
@Transactional
public interface ProductRepository extends JpaRepository<Product, String>, ProductCustomRepository {

    Optional<Product> findProductBySlug(String slug);

    /** Đồng hồ của Postgres (micro giây) — gọi đầu transaction REPEATABLE READ để làm version cho read model. */
    @Query(value = "SELECT (extract(epoch FROM clock_timestamp()) * 1000000)::bigint", nativeQuery = true)
    long snapshotMicros();

    @Query("SELECT p.id FROM Product p WHERE p.id > ?1 ORDER BY p.id")
    List<String> findIdsAfter(String after, Limit limit);

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
