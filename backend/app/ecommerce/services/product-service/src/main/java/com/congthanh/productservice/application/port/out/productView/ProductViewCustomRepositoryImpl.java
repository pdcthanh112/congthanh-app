package com.congthanh.productservice.application.port.out.productView;


import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Qualifier;

public class ProductViewCustomRepositoryImpl implements ProductViewCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Long getTotalViewOfProduct(String productId) {
        String sql = "SELECT COUNT(*) AS view_count FROM ProductView p WHERE p.productId = :productId";
        TypedQuery<Long> query = entityManager.createQuery(sql, Long.class);
        query.setParameter("productId", productId);
        return query.getSingleResult();
    }
}
