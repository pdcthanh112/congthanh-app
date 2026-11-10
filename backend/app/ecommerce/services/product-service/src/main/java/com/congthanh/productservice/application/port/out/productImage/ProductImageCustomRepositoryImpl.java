package com.congthanh.productservice.application.port.out.productImage;


import com.congthanh.productservice.domain.model.ProductImage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // fragment của Spring Data: phải nằm cùng package với interface repository
public class ProductImageCustomRepositoryImpl implements ProductImageCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ProductImage> getProductImages(String productId) {
        try {
            String sql = "SELECT i FROM ProductImage i WHERE i.product.id = :productId";
            TypedQuery<ProductImage> query = entityManager.createQuery(sql, ProductImage.class);
            query.setParameter("productId", productId);
            return query.getResultList();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public void deleteByProductId(String productId) {
        try {
            String sql = "DELETE FROM ProductImage i WHERE i.product.id = :productId";
            TypedQuery<ProductImage> query = entityManager.createQuery(sql, ProductImage.class);
            query.setParameter("productId", productId);
        } catch (NoResultException e) {
            throw new RuntimeException("Lỗi");
        }
    }
}
