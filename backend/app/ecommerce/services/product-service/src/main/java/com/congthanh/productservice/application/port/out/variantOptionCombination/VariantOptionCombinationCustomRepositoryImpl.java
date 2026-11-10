package com.congthanh.productservice.application.port.out.variantOptionCombination;


import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.VariantOption;
import com.congthanh.productservice.domain.model.VariantOptionCombination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VariantOptionCombinationCustomRepositoryImpl implements VariantOptionCombinationCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<VariantOptionCombination> findAllByProduct(Product product) {
        List<VariantOptionCombination> productOptionCombinations = new ArrayList<>();
        productOptionCombinations.add(new VariantOptionCombination(1L, Product.builder().id("366f785f-26dd-4c33-8452-0b172ef0a5de").build(), VariantOption.builder().id(16556939800219641L).build(), 1, "White"));
        productOptionCombinations.add(new VariantOptionCombination(2L, Product.builder().id("366f785f-26dd-4c33-8452-0b172ef0a5de").build(), VariantOption.builder().id(16556939800219641L).build(), 1, "Black"));
        productOptionCombinations.add(new VariantOptionCombination(3L, Product.builder().id("366f785f-26dd-4c33-8452-0b172ef0a5de").build(), VariantOption.builder().id(16556939800219641L).build(), 1, "Titan"));
        productOptionCombinations.add(new VariantOptionCombination(4L, Product.builder().id("366f785f-26dd-4c33-8452-0b172ef0a5de").build(), VariantOption.builder().id(16556939800219642L).build(), 1, "128GB"));
        productOptionCombinations.add(new VariantOptionCombination(5L, Product.builder().id("366f785f-26dd-4c33-8452-0b172ef0a5de").build(), VariantOption.builder().id(16556939800219642L).build(), 1, "256GB"));
        productOptionCombinations.add(new VariantOptionCombination(6L, Product.builder().id("366f785f-26dd-4c33-8452-0b172ef0a5de").build(), VariantOption.builder().id(16556939800219642L).build(), 1, "512GB"));
        productOptionCombinations.add(new VariantOptionCombination(7L, Product.builder().id("366f785f-26dd-4c33-8452-0b172ef0a5de").build(), VariantOption.builder().id(16556939800219642L).build(), 1, "1TB"));
        return productOptionCombinations;
//        String sql = "SELECT v FROM VariantOptionCombination v WHERE v.product.id = :productId";
//        TypedQuery<VariantOptionCombination> query = entityManager.createQuery(sql, VariantOptionCombination.class);
//        query.setParameter("productId", product.getId());
//        return query.getResultList();
    }
}
