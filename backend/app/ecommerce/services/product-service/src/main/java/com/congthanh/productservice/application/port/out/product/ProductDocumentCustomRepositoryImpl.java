package com.congthanh.productservice.application.port.out.product;


import com.congthanh.productservice.application.readmodel.ProductDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@RequiredArgsConstructor
public class ProductDocumentCustomRepositoryImpl implements ProductDocumentCustomRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<ProductDocument> findBySlug(String slug) {
        Query query = new Query();
        query.addCriteria(Criteria.where("slug").is(slug));
        ProductDocument product = mongoTemplate.findOne(query, ProductDocument.class);
        return Optional.ofNullable(product);
    }
}
