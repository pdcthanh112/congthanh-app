package com.congthanh.productservice.application.port.out.product;

import com.congthanh.productservice.application.readmodel.ProductDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductDocumentRepository extends MongoRepository<ProductDocument, String>, ProductDocumentCustomRepository {

}
