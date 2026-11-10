package com.congthanh.productservice.application.port.out.attributeValue;


import com.congthanh.productservice.domain.model.ProductAttributeValue;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

public class AttributeValueCustomRepositoryImpl implements AttributeValueCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Tuple> getAttributeOfProduct(String productId) {
        try {
            String sql = "SELECT av.id as id, pa.id as attributeId, pa.name, av.product, av.value FROM product_attribute pa JOIN attribute_value av ON pa.id = av.attribute WHERE av.product = ?1";
            Query query = entityManager.createNativeQuery(sql, Tuple.class);
            query.setParameter(1, productId);
            return query.getResultList();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public ProductAttributeValue findAttributeValueOfProduct(Long attributeId, String productId) {
        try {
            String sql = "SELECT a FROM ProductAttributeValue a WHERE a.attribute = :attributeId AND a.product = :productId";
            TypedQuery<ProductAttributeValue> query = entityManager.createQuery(sql, ProductAttributeValue.class);
            query.setParameter("attributeId", attributeId);
            query.setParameter("productId", productId);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
