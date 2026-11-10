package com.congthanh.productservice.application.port.out.product;

import com.congthanh.productservice.application.readmodel.ProductDocument;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductDocumentCustomRepository {

    Optional<ProductDocument> findBySlug(String slug);

}
