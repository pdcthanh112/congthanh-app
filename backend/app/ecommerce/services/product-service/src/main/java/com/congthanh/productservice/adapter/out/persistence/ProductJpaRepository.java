package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface ProductJpaRepository extends JpaRepository<ProductEntity, String> {

    /** Slug chỉ unique giữa các sản phẩm chưa xoá (bản đã xoá có thể trùng slug với bản đang bán). */
    @Query("SELECT p FROM ProductEntity p WHERE p.slug = ?1 AND p.status <> com.congthanh.productservice.domain.model.enums.ProductStatus.DELETED")
    Optional<ProductEntity> findProductBySlug(String slug);

    /** Đồng hồ của Postgres (micro giây) — gọi đầu transaction REPEATABLE READ để làm version cho read model. */
    @Query(value = "SELECT (extract(epoch FROM clock_timestamp()) * 1000000)::bigint", nativeQuery = true)
    long snapshotMicros();

    @Query("SELECT p.id FROM ProductEntity p WHERE p.id > ?1 ORDER BY p.id")
    List<String> findIdsAfter(String after, Limit limit);

    @EntityGraph(attributePaths = {"image", "attribute", "attribute.productAttribute", "category"})
    @Query("SELECT p FROM ProductEntity p WHERE p.id = ?1")
    Optional<ProductEntity> findDetailById(String id);

    /** Danh sách sản phẩm gốc (variant hiển thị bên trong sản phẩm gốc). */
    Page<ProductEntity> findByParentIsNullAndStatus(ProductStatus status, Pageable pageable);

    @Query(value = "SELECT p FROM ProductEntity p JOIN p.category c WHERE c.categoryId = ?1 AND p.parent IS NULL AND p.status = ?2",
            countQuery = "SELECT count(p) FROM ProductEntity p JOIN p.category c WHERE c.categoryId = ?1 AND p.parent IS NULL AND p.status = ?2")
    Page<ProductEntity> findByCategory(String categoryId, ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM ProductEntity p WHERE p.parent.id = ?1 ORDER BY p.id")
    List<ProductEntity> findVariants(String parentId);

    @Query(value = "SELECT count(*) > 0 FROM products WHERE lower(sku) = lower(?1)", nativeQuery = true)
    boolean existsSku(String sku);

    /** SKU đã thuộc sản phẩm khác (kể cả đã xoá: đơn cũ còn tham chiếu SKU). */
    @Query(value = "SELECT count(*) > 0 FROM products WHERE lower(sku) = lower(?1) AND id <> CAST(?2 AS bigint)", nativeQuery = true)
    boolean existsSkuOnOther(String sku, String productId);

    @Query(value = "SELECT count(*) > 0 FROM products WHERE slug = ?1 AND status <> 'DELETED' AND id <> CAST(?2 AS bigint)", nativeQuery = true)
    boolean existsSlugOnOther(String slug, String productId);

    @Query(value = "SELECT count(*) > 0 FROM products WHERE gtin = ?1 AND status <> 'DELETED' AND id <> CAST(?2 AS bigint)", nativeQuery = true)
    boolean existsGtinOnOther(String gtin, String productId);

    @Query(value = "SELECT count(*) > 0 FROM products WHERE parent_id = CAST(?1 AS bigint) AND variant_key = ?2 AND status <> 'DELETED'",
            nativeQuery = true)
    boolean existsLiveVariantKey(String parentId, String variantKey);

    /** Sản phẩm gốc nổi bật đang bán. */
    @Query("SELECT p FROM ProductEntity p WHERE p.parent IS NULL AND p.status = ?1 AND p.isFeatured = true")
    Page<ProductEntity> findFeatured(ProductStatus status, Pageable pageable);

    /**
     * Tra cứu ở back-office: tên chứa từ khoá (index trigram trên lower(name)) hoặc đúng SKU. {@code pattern} đã
     * escape ký tự LIKE. Storefront tìm kiếm/lọc qua search-service.
     */
    @Query(value = """
            SELECT * FROM products
            WHERE parent_id IS NULL AND status <> 'DELETED'
              AND (lower(name) LIKE ?1 ESCAPE '\\' OR lower(sku) = lower(?2))
            ORDER BY (lower(sku) = lower(?2)) DESC, public.similarity(lower(name), lower(?2)) DESC, id
            LIMIT ?3""", nativeQuery = true)
    List<ProductEntity> lookup(String pattern, String keyword, int limit);

}
